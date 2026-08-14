# Deposit Account Service API and Dependency Contract

Status: current implementation baseline, verified against the Java controllers and DTO records on 2026-08-14.

This document answers two different contract questions:

1. Which APIs are owned and exposed by `deposit-account-service`?
2. Which provider APIs must other services expose so that Deposit can run with `STUB_UPSTREAM_CLIENTS=false`?

The JSON shown under **Required request JSON** is the caller's body. The JSON shown under **Response JSON** is returned by the API. Fields marked optional may be omitted or sent as `null`; every other request field shown is required.

## 1. Common HTTP contract

- Public base URL through the gateway: `http://localhost:8080`.
- Direct service URL: `http://localhost:8086`.
- Send `Content-Type: application/json` for requests with a body.
- Send `X-Correlation-Id` on every request. The service generates one when absent.
- Send `Idempotency-Key` on every mutation explicitly marked below.
- When security is enabled, public APIs require a user bearer token. Payment-operation APIs require `SCOPE_deposit-payment:write`.
- Timestamps are ISO-8601 with an offset, normally UTC, for example `2026-08-13T12:00:00Z`.
- Dates use `yyyy-MM-dd`; currency codes use three uppercase letters.
- Monetary request values must be JSON numbers, not quoted strings.

Standard error response:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "status": 400,
  "path": "/api/deposit-accounts",
  "correlationId": "corr-20260814-001",
  "timestamp": "2026-08-14T10:00:00Z",
  "errors": [
    {
      "field": "currency",
      "message": "must match [A-Z]{3}"
    }
  ]
}
```

## 2. Service-wise dependency map

| Service | Direction | Required APIs | Runtime state |
|---|---|---|---|
| API Gateway | Client -> Deposit | Routes `/api/deposit-accounts/**`, `/api/internal/deposit-accounts/**`, `/api/internal/deposit-payment-operations/**` | Configured |
| CIF Service | Deposit -> CIF | Customer/KYC eligibility snapshot | Implemented client; stubbed by default |
| KYC Service | Indirect | No direct call; CIF supplies synchronized `kycStatus` | Intentional boundary |
| Product Master Service | Deposit -> Product | Product definition and account-opening validation | Implemented client; stubbed by default |
| Accounting Service | Deposit -> Accounting | Authoritative ledger balance read | Implemented client; stubbed by default |
| Payments Service | Payments -> Deposit | Eligibility, reserve, settle/capture, release, operation status | Deposit provider APIs implemented |
| Credit Card Service | Indirect through Payments | Deposit receives `creditCardAccountId`; Deposit does not call Card | Orchestration belongs to Payments |
| EOD/Reconciliation Service | EOD -> Deposit | Accrual, maturity, and readiness commands | Deposit provider APIs implemented; direct-service routes |
| Statement Service | Statement -> Deposit | Peer-safe account/fixed-deposit snapshot | Fixed-deposit snapshot exists; generic internal account snapshot is missing |
| Identity/Authorization | Caller -> Deposit | JWT and scopes | Resource-server support configured |
| Eureka Discovery | Deposit <-> registry | Service registration and logical-name resolution | Configured |

## 3. Provider APIs that Deposit calls

These three services are the actual synchronous outbound dependencies in the current source code.

### 3.1 CIF Service

API: `GET /api/v1/cifs/{cifId}/deposit-creation-details`

Path field: `cifId` is required.

Request JSON: none.

Response JSON — all fields are required for the current eligibility decision; `dateOfBirth` may technically be null but then calculated age becomes `0`. A successful response containing the CIF record means the customer is active:

```json
{
  "cifId": "CIF-1001",
  "dateOfBirth": "1990-05-20",
  "customerType": "INDIVIDUAL",
  "kycStatus": "VERIFIED"
}
```

Required behavior:

- Return `404` when the CIF does not exist.
- A `200` response containing customer information means the CIF is active. `kycStatus` must still be `VERIFIED` for account opening, additional holders, mandates, or fixed-deposit booking.
- Deposit does not call KYC directly.

### 3.2 Product Master Service — product definition

API: `GET /api/products/{productCode}`

Path field: `productCode` is required.

Request JSON: none.

Response JSON — CASA uses the core fields; fixed deposits additionally require the nested rules and rate slabs:

```json
{
  "productCode": "FD-12M-001",
  "version": 3,
  "productName": "12 Month Fixed Deposit",
  "category": "DEPOSIT",
  "subtype": "FIXED_DEPOSIT",
  "currencyCode": "INR",
  "accountType": "FIXED_DEPOSIT",
  "status": "ACTIVE",
  "interestRule": {
    "annualInterestRate": 6.75,
    "policyVersion": "FD-POLICY-V3",
    "interestCalculationMethod": "COMPOUND_INTEREST",
    "interestPostingFrequency": "AT_MATURITY",
    "compoundingFrequency": "QUARTERLY",
    "dayCountConvention": "ACTUAL_365"
  },
  "amountRule": {
    "minimumAmount": 1000.00,
    "maximumAmount": 10000000.00,
    "minimumTenureMonths": 1,
    "maximumTenureMonths": 120
  },
  "fixedDepositRule": {
    "allowedTenureUnits": ["MONTH"],
    "allowedInterestPayoutFrequencies": ["AT_MATURITY"],
    "defaultInterestPayoutFrequency": "AT_MATURITY",
    "compoundingFrequency": "QUARTERLY",
    "dayCountConvention": "ACTUAL_365"
  },
  "interestRateSlabs": [
    {
      "slabCode": "FD-12M-GENERAL",
      "minimumTenure": 12,
      "maximumTenure": 12,
      "tenureUnit": "MONTH",
      "minimumAmount": 1000.00,
      "maximumAmount": 10000000.00,
      "customerCategory": "GENERAL",
      "annualInterestRate": 6.75,
      "effectiveFrom": "2026-04-01",
      "effectiveTo": "2027-03-31",
      "active": true
    }
  ]
}
```

CASA rules: `status=ACTIVE`, version and currency must match, and subtype/accountType must resolve to `SAVINGS` or `CURRENT`.

Fixed-deposit rules: `category=DEPOSIT`, subtype `FIXED_DEPOSIT`, version/currency match, amount and tenure are within bounds, and exactly one active applicable rate slab must resolve unless a default annual rate exists.

### 3.3 Product Master Service — opening validation

API: `POST /api/products/{productCode}/validate-account-opening`

Path field: `productCode` is required.

Required request JSON:

```json
{
  "openingAmount": 0.00,
  "age": 36,
  "customerType": "INDIVIDUAL",
  "kycCompleted": true
}
```

Response JSON:

```json
{
  "eligible": true
}
```

### 3.4 Accounting Service — balance read

API: `GET /internal/v1/account-balances/{accountReference}`

Path field: `accountReference` is required and is the Deposit `accountId`.

Request JSON: none.

Response JSON:

```json
{
  "accountReference": "dep-acc-001",
  "ledgerBalance": 25000.00,
  "currency": "INR",
  "asOf": "2026-08-14T10:00:00Z"
}
```

Current behavior note: only `accountReference`, `ledgerBalance`, and `currency` are consumed. The Deposit balance endpoint combines this ledger value with its local available/blocked projection.

## 4. Public CASA APIs owned by Deposit

### 4.1 Check opening eligibility

API: `POST /api/deposit-accounts/eligibility-check`

Required request JSON:

```json
{
  "customerId": "CIF-1001",
  "productId": "SAV-001",
  "productVersion": 1,
  "currency": "INR",
  "openingAmount": 0.00
}
```

Response JSON:

```json
{
  "eligible": true,
  "decisionCode": "ELIGIBLE",
  "productName": "Standard Savings Account",
  "evaluatedAt": "2026-08-14T10:00:00Z"
}
```

### 4.2 Open an account

API: `POST /api/deposit-accounts`

Required header: `Idempotency-Key`. Optional request fields: `nominees`, `externalReference`.

Required request JSON:

```json
{
  "customerIds": ["CIF-1001"],
  "primaryCustomerId": "CIF-1001",
  "productId": "SAV-001",
  "productVersion": 1,
  "currency": "INR",
  "openingAmount": 0.00,
  "servicingBranchId": "BR-001",
  "operatingInstruction": "SINGLE",
  "nominees": [],
  "channel": "BRANCH",
  "externalReference": "ORIGINATION-001"
}
```

The validated `openingAmount` is recorded in the account's local balance projection when the account is opened.
In a production deployment, the corresponding payment or accounting posting must be coordinated by the payment workflow.

Response: `201 Created`, `Location` and `ETag` headers.

Response JSON:

```json
{
  "accountId": "dep-acc-001",
  "maskedAccountNumber": "********9012",
  "status": "PENDING_ACTIVATION",
  "product": {
    "productId": "SAV-001",
    "version": 1,
    "name": "Standard Savings Account"
  },
  "currency": "INR",
  "servicingBranchId": "BR-001",
  "operatingInstruction": "SINGLE",
  "holders": [
    {
      "customerId": "CIF-1001",
      "role": "PRIMARY",
      "authorizationType": null,
      "ownershipPercentage": null,
      "status": "ACTIVE"
    }
  ],
  "nominees": [],
  "mandates": [],
  "limits": [],
  "balance": {
    "ledger": 0.00,
    "available": 0.00,
    "blocked": 0.00,
    "currency": "INR",
    "asOf": "2026-08-14T10:00:00Z",
    "projectionVersion": 0,
    "stale": false
  },
  "openedAt": "2026-08-14T10:00:00Z",
  "createdAt": "2026-08-14T10:00:00Z",
  "version": 0
}
```

### 4.3 Get account detail

API: `GET /api/deposit-accounts/{accountId}`

Request JSON: none.

Response JSON: same `AccountDetailView` shape returned by API 4.2, with current values. Response includes `ETag`.

### 4.4 Search accounts

API: `GET /api/deposit-accounts?customerId={cifId}&status={status}&page=0&size=25`

All query fields are optional. `size` is clamped to `1..100`. Status values: `PENDING_ACTIVATION`, `ACTIVE`, `BLOCKED`, `FROZEN`, `DORMANT`, `CLOSURE_PENDING`, `CLOSED`.

Request JSON: none.

Response JSON:

```json
{
  "content": [
    {
      "accountId": "dep-acc-001",
      "maskedAccountNumber": "********9012",
      "productName": "Standard Savings Account",
      "currency": "INR",
      "status": "ACTIVE",
      "availableBalance": 25000.00,
      "balanceAsOf": "2026-08-14T10:00:00Z",
      "servicingBranchId": "BR-001",
      "version": 1
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 25,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

Spring may also serialize `pageable` and `sort` metadata; consumers should tolerate those additional fields.

### 4.5 Get balance

API: `GET /api/deposit-accounts/{accountId}/balance`

Request JSON: none.

Response JSON:

```json
{
  "ledger": 25000.00,
  "available": 23750.00,
  "blocked": 1250.00,
  "currency": "INR",
  "asOf": "2026-08-14T10:00:00Z",
  "projectionVersion": 4,
  "stale": false
}
```

### 4.6 Get status history

API: `GET /api/deposit-accounts/{accountId}/status-history`

Request JSON: none.

Response JSON:

```json
[
  {
    "fromStatus": "PENDING_ACTIVATION",
    "toStatus": "ACTIVE",
    "reasonCode": "OPENING_CHECKS_COMPLETE",
    "reasonText": "Opening checks completed",
    "changedBy": "user-1001",
    "actorType": "USER",
    "changedAt": "2026-08-14T10:05:00Z",
    "correlationId": "corr-20260814-001"
  }
]
```

### 4.7 Add a holder

API: `POST /api/deposit-accounts/{accountId}/holders`

Required header: `Idempotency-Key`. Optional request fields: `authorizationType`, `ownershipPercentage`.

Required request JSON:

```json
{
  "customerId": "CIF-1002",
  "role": "JOINT",
  "authorizationType": "EITHER_OR_SURVIVOR",
  "ownershipPercentage": 50.00
}
```

Response: `201 Created`.

Response JSON: same `AccountDetailView` shape as API 4.2, with the new holder in `holders`.

### 4.8 Remove a holder

API: `DELETE /api/deposit-accounts/{accountId}/holders/{customerId}`

Required header: `Idempotency-Key`.

Request JSON: none.

Response: `204 No Content`; no response JSON.

### 4.9 Replace nominees

API: `PUT /api/deposit-accounts/{accountId}/nominees`

Required header: `Idempotency-Key`. `customerReference` is optional. Allocations must total exactly `100.00` when the list is non-empty.

Required request JSON:

```json
[
  {
    "customerReference": "CIF-9001",
    "name": "Nominee One",
    "relationshipCode": "SPOUSE",
    "allocationPercentage": 100.00
  }
]
```

Response JSON:

```json
[
  {
    "nomineeId": "nominee-001",
    "customerReference": "CIF-9001",
    "relationshipCode": "SPOUSE",
    "allocationPercentage": 100.00,
    "status": "ACTIVE"
  }
]
```

The nominee name is intentionally not returned because it is protected PII.

### 4.10 Upsert an account limit

API: `PUT /api/deposit-accounts/{accountId}/limits/{limitType}`

Required header: `Idempotency-Key`. Optional field: `effectiveTo`. Path and body `limitType` must match.

Required request JSON:

```json
{
  "limitType": "DAILY_DEBIT",
  "amount": 100000.00,
  "currency": "INR",
  "effectiveFrom": "2026-08-14T00:00:00Z",
  "effectiveTo": null
}
```

Response JSON:

```json
{
  "type": "DAILY_DEBIT",
  "amount": 100000.00,
  "currency": "INR",
  "effectiveFrom": "2026-08-14T00:00:00Z",
  "effectiveTo": null
}
```

Limit types: `DAILY_DEBIT`, `DAILY_CREDIT`, `SINGLE_TRANSACTION`, `CHANNEL_TRANSFER`.

### 4.11 Add a mandate

API: `POST /api/deposit-accounts/{accountId}/mandates`

Required header: `Idempotency-Key`. Optional field: `validTo`.

Required request JSON:

```json
{
  "authorizedCustomerId": "CIF-1003",
  "mandateType": "GENERAL",
  "validFrom": "2026-08-14T00:00:00Z",
  "validTo": "2027-08-13T23:59:59Z"
}
```

Response: `201 Created`.

Response JSON:

```json
{
  "mandateId": "mandate-001",
  "authorizedCustomerId": "CIF-1003",
  "mandateType": "GENERAL",
  "status": "ACTIVE",
  "validFrom": "2026-08-14T00:00:00Z",
  "validTo": "2027-08-13T23:59:59Z"
}
```

### 4.12 Revoke a mandate

API: `DELETE /api/deposit-accounts/{accountId}/mandates/{mandateId}`

Required header: `Idempotency-Key`.

Request JSON: none.

Response: `204 No Content`; no response JSON.

### 4.13 Execute a lifecycle command

API: `POST /api/deposit-accounts/{accountId}/commands/{command}`

Required header: `Idempotency-Key`. Optional header: `If-Match: "{version}"`. Optional request fields: `reasonText`, `effectiveAt`.

Commands: `activate`, `block`, `unblock`, `freeze`, `release-freeze`, `mark-dormant`, `reactivate`, `request-close`, `confirm-close`.

Required request JSON:

```json
{
  "reasonCode": "CUSTOMER_REQUEST",
  "reasonText": "Customer requested temporary block",
  "effectiveAt": "2026-08-14T10:30:00Z"
}
```

Response JSON: same `AccountDetailView` shape as API 4.2, with the updated status and version.

## 5. Public CASA closure APIs owned by Deposit

### 5.1 Get a closure quote

API: `POST /api/deposit-accounts/{accountId}/closure-quotes`

Optional field: `destinationAccountId`.

Required request JSON:

```json
{
  "customerId": "CIF-1001",
  "destinationAccountId": "dep-acc-002",
  "channel": "BRANCH",
  "requestedClosureDate": "2026-08-14"
}
```

Response JSON:

```json
{
  "eligible": true,
  "accountId": "dep-acc-001",
  "accountSubtype": "SAVINGS",
  "currentBalance": 0.00,
  "closureFee": 0.00,
  "netSettlementAmount": 0.00,
  "currency": "INR",
  "destinationAccountId": "dep-acc-002",
  "blockers": [],
  "quoteValidUntil": "2026-08-14T10:15:00Z"
}
```

### 5.2 Create a closure request

API: `POST /api/deposit-accounts/{accountId}/closure-requests`

Required header: `Idempotency-Key`. Optional fields: `destinationAccountId`, `reasonText`.

Required request JSON:

```json
{
  "customerId": "CIF-1001",
  "destinationAccountId": "dep-acc-002",
  "channel": "BRANCH",
  "reasonCode": "CUSTOMER_REQUEST",
  "reasonText": "Account no longer required",
  "requestedClosureDate": "2026-08-14"
}
```

Response: `201 Created`.

Response JSON:

```json
{
  "closureRequestId": "close-req-001",
  "accountId": "dep-acc-001",
  "closureType": "CASA_CUSTOMER_REQUEST",
  "status": "CLOSED",
  "requestedBy": "CIF-1001",
  "requestedChannel": "BRANCH",
  "requestedDate": "2026-08-14",
  "reasonCode": "CUSTOMER_REQUEST",
  "reasonText": "Account no longer required",
  "destinationAccountId": "dep-acc-002",
  "rejectionCode": null,
  "rejectionDetails": null,
  "policyVersion": "CASA-CLOSE-V1",
  "checks": [
    {
      "code": "ACCOUNT_SUBTYPE",
      "status": "PASSED",
      "details": "Check passed",
      "checkedAt": "2026-08-14T10:00:00Z"
    }
  ],
  "settlement": {
    "principalAmount": 0.00,
    "originalInterestAmount": 0.00,
    "recalculatedInterestAmount": 0.00,
    "interestPenaltyAmount": 0.00,
    "closureFeeAmount": 0.00,
    "taxAmount": 0.00,
    "netPayoutAmount": 0.00,
    "currency": "INR",
    "destinationAccountId": "dep-acc-002",
    "transactionReference": "CASA-CLOSE-...",
    "status": "COMPLETED"
  },
  "createdAt": "2026-08-14T10:00:00Z",
  "completedAt": "2026-08-14T10:00:01Z",
  "version": 1
}
```

### 5.3 Get one closure request

API: `GET /api/deposit-accounts/{accountId}/closure-requests/{requestId}`

Request JSON: none.

Response JSON: same `ClosureRequestView` shape as API 5.2.

### 5.4 List closure history

API: `GET /api/deposit-accounts/{accountId}/closure-requests`

Request JSON: none.

Response JSON:

```json
[
  {
    "closureRequestId": "close-req-001",
    "accountId": "dep-acc-001",
    "closureType": "CASA_CUSTOMER_REQUEST",
    "status": "CLOSED",
    "requestedBy": "CIF-1001",
    "requestedChannel": "BRANCH",
    "requestedDate": "2026-08-14",
    "reasonCode": "CUSTOMER_REQUEST",
    "reasonText": "Account no longer required",
    "destinationAccountId": "dep-acc-002",
    "rejectionCode": null,
    "rejectionDetails": null,
    "policyVersion": "CASA-CLOSE-V1",
    "checks": [],
    "settlement": null,
    "createdAt": "2026-08-14T10:00:00Z",
    "completedAt": "2026-08-14T10:00:01Z",
    "version": 1
  }
]
```

### 5.5 Cancel a closure request

API: `POST /api/deposit-accounts/{accountId}/closure-requests/{requestId}/cancel`

Required header: `Idempotency-Key`.

Required request JSON:

```json
{
  "reasonCode": "CUSTOMER_WITHDRAWN"
}
```

Response JSON: same `ClosureRequestView` shape as API 5.2, with `status=CANCELLED`.

## 6. Public fixed-deposit APIs owned by Deposit

### 6.1 Quote a fixed deposit

API: `POST /api/deposit-accounts/fixed-deposits/quotes`

Required request JSON:

```json
{
  "customerId": "CIF-1001",
  "productCode": "FD-12M-001",
  "productVersion": 3,
  "principal": 100000.00,
  "currency": "INR",
  "tenureValue": 12,
  "tenureUnit": "MONTH",
  "interestPayoutFrequency": "AT_MATURITY",
  "valueDate": "2026-08-14"
}
```

Response JSON:

```json
{
  "productCode": "FD-12M-001",
  "productVersion": 3,
  "productName": "12 Month Fixed Deposit",
  "rateSlabCode": "FD-12M-GENERAL",
  "annualInterestRate": 6.75,
  "principal": 100000.00,
  "valueDate": "2026-08-14",
  "maturityDate": "2027-08-14",
  "expectedInterest": 6968.00,
  "expectedMaturityAmount": 106968.00,
  "calculationMethod": "COMPOUND_INTEREST",
  "compoundingFrequency": "QUARTERLY",
  "dayCountConvention": "ACTUAL_365"
}
```

### 6.2 Book a fixed deposit

API: `POST /api/deposit-accounts/fixed-deposits`

Required header: `Idempotency-Key`. Optional fields: `nominees`, `externalReference`.

Required request JSON:

```json
{
  "customerIds": ["CIF-1001"],
  "primaryCustomerId": "CIF-1001",
  "productCode": "FD-12M-001",
  "productVersion": 3,
  "principal": 100000.00,
  "currency": "INR",
  "tenureValue": 12,
  "tenureUnit": "MONTH",
  "interestPayoutFrequency": "AT_MATURITY",
  "fundingAccountId": "dep-acc-001",
  "payoutAccountId": "dep-acc-001",
  "servicingBranchId": "BR-001",
  "nominees": [],
  "channel": "ONLINE",
  "externalReference": "FD-BOOKING-001"
}
```

Response: `201 Created`.

Response JSON:

```json
{
  "fixedDepositId": "fd-001",
  "accountId": "fd-account-001",
  "maskedAccountNumber": "********4321",
  "productCode": "FD-12M-001",
  "productVersion": 3,
  "status": "ACTIVE",
  "principal": 100000.00,
  "currency": "INR",
  "annualInterestRate": 6.75,
  "valueDate": "2026-08-14",
  "maturityDate": "2027-08-14",
  "expectedInterest": 6968.00,
  "expectedMaturityAmount": 106968.00,
  "accruedInterest": 0.00,
  "fundingAccountId": "dep-acc-001",
  "payoutAccountId": "dep-acc-001",
  "version": 1
}
```

### 6.3 Get a fixed deposit

API: `GET /api/deposit-accounts/fixed-deposits/{fdId}`

Request JSON: none.

Response JSON: same `FixedDepositView` shape as API 6.2.

### 6.4 Search fixed deposits

API: `GET /api/deposit-accounts/fixed-deposits?customerId={cifId}&status={status}&maturingBefore={date}&page=0&size=20`

All query fields are optional. `size` is clamped to `1..100`.

Request JSON: none.

Response JSON:

```json
{
  "content": [
    {
      "fixedDepositId": "fd-001",
      "accountId": "fd-account-001",
      "maskedAccountNumber": "********4321",
      "productCode": "FD-12M-001",
      "productVersion": 3,
      "status": "ACTIVE",
      "principal": 100000.00,
      "currency": "INR",
      "annualInterestRate": 6.75,
      "valueDate": "2026-08-14",
      "maturityDate": "2027-08-14",
      "expectedInterest": 6968.00,
      "expectedMaturityAmount": 106968.00,
      "accruedInterest": 0.00,
      "fundingAccountId": "dep-acc-001",
      "payoutAccountId": "dep-acc-001",
      "version": 1
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

### 6.5 Get interest accruals

API: `GET /api/deposit-accounts/fixed-deposits/{fdId}/interest-accruals`

Request JSON: none.

Response JSON:

```json
[
  {
    "businessDate": "2026-08-14",
    "accrualBase": 100000.00,
    "annualRate": 6.75,
    "interestAmount": 18.49,
    "cumulativeInterest": 18.49,
    "status": "POSTED",
    "createdAt": "2026-08-14T23:00:00Z"
  }
]
```

### 6.6 Get projected schedule

API: `GET /api/deposit-accounts/fixed-deposits/{fdId}/projected-schedule`

Request JSON: none.

Response JSON:

```json
{
  "fixedDepositId": "fd-001",
  "valueDate": "2026-08-14",
  "maturityDate": "2027-08-14",
  "principal": 100000.00,
  "annualInterestRate": 6.75,
  "projectedInterest": 6968.00,
  "projectedMaturityAmount": 106968.00,
  "payoutFrequency": "AT_MATURITY"
}
```

## 7. Public fixed-deposit premature-closure APIs

### 7.1 Get a premature-closure quote

API: `POST /api/deposit-accounts/fixed-deposits/{fdId}/premature-closure-quotes`

Required request JSON:

```json
{
  "customerId": "CIF-1001",
  "destinationAccountId": "dep-acc-001",
  "channel": "BRANCH",
  "requestedClosureDate": "2026-11-14"
}
```

Response JSON:

```json
{
  "eligible": true,
  "fixedDepositId": "fd-001",
  "principal": 100000.00,
  "bookedAnnualRate": 6.75,
  "completedHoldingDays": 92,
  "applicableAnnualRate": 5.50,
  "penaltyRate": 1.00,
  "finalAnnualRate": 4.50,
  "originalExpectedInterest": 6968.00,
  "recalculatedInterest": 1134.25,
  "interestRecoveryAmount": 0.00,
  "netPayoutAmount": 101134.25,
  "currency": "INR",
  "destinationAccountId": "dep-acc-001",
  "blockers": [],
  "quoteValidUntil": "2026-08-14T10:15:00Z"
}
```

### 7.2 Create a premature-closure request

API: `POST /api/deposit-accounts/fixed-deposits/{fdId}/premature-closure-requests`

Required header: `Idempotency-Key`. Optional field: `reasonText`.

Required request JSON:

```json
{
  "customerId": "CIF-1001",
  "destinationAccountId": "dep-acc-001",
  "channel": "BRANCH",
  "reasonCode": "CUSTOMER_REQUEST",
  "reasonText": "Funds required early",
  "requestedClosureDate": "2026-11-14"
}
```

Response: `201 Created`.

Response JSON: same `ClosureRequestView` shape as API 5.2, with `closureType=FD_PREMATURE` and the calculated settlement amounts.

### 7.3 Get a premature-closure request

API: `GET /api/deposit-accounts/fixed-deposits/{fdId}/premature-closure-requests/{requestId}`

Request JSON: none.

Response JSON: same `ClosureRequestView` shape as API 5.2.

## 8. Deposit internal APIs grouped by consumer service

### 8.1 Payments Service — account eligibility

API: `GET /api/internal/deposit-accounts/{accountId}/eligibility`

Request JSON: none.

Response JSON:

```json
{
  "accountId": "dep-acc-001",
  "status": "ACTIVE",
  "debitAllowed": true,
  "creditAllowed": true,
  "currency": "INR",
  "limits": [
    {
      "type": "DAILY_DEBIT",
      "amount": 100000.00,
      "currency": "INR",
      "effectiveFrom": "2026-08-14T00:00:00Z",
      "effectiveTo": null
    }
  ],
  "evaluatedAt": "2026-08-14T10:00:00Z"
}
```

### 8.2 Payments Service — reserve a book transfer

API: `POST /api/internal/deposit-payment-operations/book-transfers/reservations`

Required header: `Idempotency-Key`. Optional field: `expiresAt`; when absent, the service supplies an expiry.

`expiresAt` must be later than the current time and no more than 30 minutes in the future; the default is five minutes.

Required request JSON:

```json
{
  "paymentId": "PAY-BOOK-20260814-001",
  "requestorCustomerId": "CIF-1001",
  "sourceAccountId": "dep-acc-001",
  "targetAccountId": "dep-acc-002",
  "amount": 1250.00,
  "currencyCode": "INR",
  "expiresAt": "2026-08-14T12:00:00Z"
}
```

Response: `201 Created`.

Response JSON:

```json
{
  "reservationId": "res-001",
  "paymentId": "PAY-BOOK-20260814-001",
  "operationType": "BOOK_TRANSFER",
  "status": "ACTIVE",
  "sourceAccountId": "dep-acc-001",
  "targetAccountId": "dep-acc-002",
  "externalTargetId": null,
  "amount": 1250.00,
  "currencyCode": "INR",
  "expiresAt": "2026-08-14T12:00:00Z",
  "transactionIds": ["txn-hold-001"]
}
```

### 8.3 Payments Service — settle a book transfer

API: `POST /api/internal/deposit-payment-operations/book-transfers/{paymentId}/settle`

Required header: `Idempotency-Key`.

Required request JSON:

```json
{
  "reservationId": "res-001"
}
```

Response JSON:

```json
{
  "reservationId": "res-001",
  "paymentId": "PAY-BOOK-20260814-001",
  "operationType": "BOOK_TRANSFER",
  "status": "SETTLED",
  "sourceAccountId": "dep-acc-001",
  "targetAccountId": "dep-acc-002",
  "externalTargetId": null,
  "amount": 1250.00,
  "currencyCode": "INR",
  "expiresAt": "2026-08-14T12:00:00Z",
  "transactionIds": ["txn-hold-001", "txn-debit-001", "txn-credit-001"]
}
```

### 8.4 Payments Service — reserve a credit-card repayment

API: `POST /api/internal/deposit-payment-operations/credit-card-repayments/reservations`

Required header: `Idempotency-Key`. Optional field: `expiresAt`.

`expiresAt` follows the same five-minute default and 30-minute maximum as a book-transfer reservation.

Required request JSON:

```json
{
  "paymentId": "PAY-CARD-20260813-001",
  "requestorCustomerId": "CIF-1001",
  "sourceAccountId": "dep-acc-001",
  "creditCardAccountId": "card-acc-001",
  "amount": 1250.00,
  "currencyCode": "INR",
  "expiresAt": "2026-08-13T12:00:00Z"
}
```

Response: `201 Created`.

Response JSON:

```json
{
  "reservationId": "res-card-001",
  "paymentId": "PAY-CARD-20260813-001",
  "operationType": "CREDIT_CARD_REPAYMENT",
  "status": "ACTIVE",
  "sourceAccountId": "dep-acc-001",
  "targetAccountId": null,
  "externalTargetId": "card-acc-001",
  "amount": 1250.00,
  "currencyCode": "INR",
  "expiresAt": "2026-08-13T12:00:00Z",
  "transactionIds": ["txn-hold-card-001"]
}
```

### 8.5 Payments Service — capture a credit-card repayment

API: `POST /api/internal/deposit-payment-operations/credit-card-repayments/{paymentId}/capture`

Required header: `Idempotency-Key`.

Required request JSON:

```json
{
  "reservationId": "res-card-001"
}
```

Response JSON:

```json
{
  "reservationId": "res-card-001",
  "paymentId": "PAY-CARD-20260813-001",
  "operationType": "CREDIT_CARD_REPAYMENT",
  "status": "CAPTURED",
  "sourceAccountId": "dep-acc-001",
  "targetAccountId": null,
  "externalTargetId": "card-acc-001",
  "amount": 1250.00,
  "currencyCode": "INR",
  "expiresAt": "2026-08-13T12:00:00Z",
  "transactionIds": ["txn-hold-card-001", "txn-debit-card-001"]
}
```

### 8.6 Payments Service — release a reservation

API: `POST /api/internal/deposit-payment-operations/reservations/{reservationId}/release`

Required header: `Idempotency-Key`. Optional field: `reasonCode`.

Required request JSON:

```json
{
  "paymentId": "PAY-CARD-20260813-001",
  "reasonCode": "DOWNSTREAM_FAILURE"
}
```

Response JSON: same payment-operation shape as API 8.4, with `status=RELEASED` and a hold-release transaction ID appended.

### 8.7 Payments Service — read operation status

API: `GET /api/internal/deposit-payment-operations/{paymentId}`

Request JSON: none.

Response JSON: same payment-operation shape as APIs 8.2–8.6 with the current status and transaction IDs.

### 8.8 EOD/Reconciliation Service — run CASA accrual controls

API: `POST /internal/v1/deposit-accounts/eod/accruals`

Required request JSON:

```json
{
  "eodRunId": "EOD-20260814",
  "commandReference": "DEP-ACCRUAL-20260814-V1",
  "businessDate": "2026-08-14",
  "currency": "INR"
}
```

`businessDate` has no bean-validation annotation in the current DTO but should be treated as required by contract.

Response JSON:

```json
{
  "eodRunId": "EOD-20260814",
  "commandReference": "DEP-ACCRUAL-20260814-V1",
  "businessDate": "2026-08-14",
  "processedCount": 1250,
  "failedCount": 0,
  "totalAmount": 54231.18,
  "failures": []
}
```

### 8.9 EOD/Reconciliation Service — CASA readiness

API: `GET /internal/v1/deposit-accounts/eod/readiness`

Request JSON: none.

Response JSON:

```json
{
  "service": "deposit-account-service",
  "businessDate": "2026-08-14",
  "ready": true,
  "blockers": []
}
```

### 8.10 EOD/Reconciliation Service — fixed-deposit accruals

API: `POST /internal/v1/deposit-accounts/eod/fixed-deposit-accruals`

Required header: `Idempotency-Key`.

Required request JSON:

```json
{
  "eodRunId": "EOD-20260814",
  "businessDate": "2026-08-14",
  "commandReference": "FD-ACCRUAL-20260814-V1"
}
```

Response JSON:

```json
{
  "eodRunId": "EOD-20260814",
  "businessDate": "2026-08-14",
  "commandReference": "FD-ACCRUAL-20260814-V1",
  "processed": 750,
  "skipped": 4,
  "totalAmount": 18450.72,
  "failures": []
}
```

### 8.11 EOD/Reconciliation Service — fixed-deposit maturities

API: `POST /internal/v1/deposit-accounts/eod/fixed-deposit-maturities`

Required header: `Idempotency-Key`.

Required request JSON:

```json
{
  "eodRunId": "EOD-20260814",
  "businessDate": "2026-08-14",
  "commandReference": "FD-MATURITY-20260814-V1"
}
```

Response JSON: same `EodResult` shape as API 8.10.

### 8.12 EOD/Reconciliation Service — fixed-deposit readiness

API: `GET /internal/v1/deposit-accounts/eod/fixed-deposit-readiness`

Request JSON: none.

Response JSON:

```json
{
  "ready": true,
  "pendingFunding": 0,
  "pendingPayouts": 0,
  "blockers": []
}
```

### 8.13 Statement/peer service — fixed-deposit snapshot

API: `GET /internal/v1/deposit-accounts/fixed-deposits/{fdId}`

Request JSON: none.

Response JSON: same `FixedDepositView` shape as API 6.2.

### 8.14 Peer service — closure request snapshot

API: `GET /internal/v1/deposit-accounts/closures/{requestId}`

Request JSON: none.

Response JSON: same `ClosureRequestView` shape as API 5.2.

## 9. Required enum values

| Field family | Allowed values |
|---|---|
| Account status | `PENDING_ACTIVATION`, `ACTIVE`, `BLOCKED`, `FROZEN`, `DORMANT`, `CLOSURE_PENDING`, `CLOSED` |
| Product subtype | `SAVINGS`, `CURRENT`, `FIXED_DEPOSIT` |
| Fixed-deposit status | `PENDING_FUNDING`, `ACTIVE`, `FUNDING_FAILED`, `MATURED`, `PREMATURE_CLOSURE_REQUESTED`, `PAYOUT_PENDING`, `PAID_OUT`, `CLOSED_PREMATURE` |
| Holder role | `PRIMARY`, `JOINT`, `AUTHORIZED` |
| Operating instruction | `SINGLE`, `JOINTLY`, `EITHER_OR_SURVIVOR`, `ANYONE_OR_SURVIVOR` |
| Limit type | `DAILY_DEBIT`, `DAILY_CREDIT`, `SINGLE_TRANSACTION`, `CHANNEL_TRANSFER` |
| Tenure unit | `DAY`, `MONTH` (production Product Master currently accepts fixed-deposit rules only for `MONTH`) |
| Interest payout frequency | `AT_MATURITY` |
| Payment operation | `BOOK_TRANSFER`, `CREDIT_CARD_REPAYMENT`, `FIXED_DEPOSIT_FUNDING`, `FIXED_DEPOSIT_MATURITY_PAYOUT`, `CASA_ACCOUNT_CLOSURE`, `FIXED_DEPOSIT_PREMATURE_PAYOUT` |
| Reservation status | `ACTIVE`, `CAPTURED`, `SETTLED`, `RELEASED`, `EXPIRED` |
| Closure request status | `REQUESTED`, `VALIDATING`, `REJECTED`, `SETTLEMENT_PENDING`, `READY_TO_CLOSE`, `PAYOUT_PENDING`, `CLOSED`, `SETTLEMENT_FAILED`, `CANCELLED` |

## 10. Contract gaps to resolve before production

1. **Product integration resolved.** Deposit calls the canonical `/internal/v1/products/{productCode}/validate-account-opening` route through Eureka, while Product Master exposes `/api/v1/products/**` and retains `/api/products/**` as a compatibility alias. Product Master returns the resolved FD slab and Deposit snapshots it at booking.
2. **CIF visibility mismatch.** Current code calls public-looking `/api/v1/cifs/{id}/deposit-creation-details`; the target contract calls `/internal/v1/cifs/{id}/deposit-creation-details`. This should be an authenticated internal route.
3. **Internal Deposit prefix mismatch.** Payment APIs use `/api/internal/**`, while EOD/peer APIs use `/internal/v1/**`. Standardize internal APIs under `/internal/v1/**` or publish an explicit compatibility period.
4. **Gateway route gap.** The gateway does not route `/internal/v1/**`. This is acceptable only if EOD and peer services call port `8086` directly through service discovery.
5. **Generic peer snapshot missing.** The target contract expects `GET /internal/v1/deposit-accounts/{accountId}` for Statement and other peers; only the public account detail endpoint currently exists.
6. **Accounting mutation gap.** Book-transfer settlement, fixed-deposit funding/maturity, and closures currently update Deposit's local balance projection directly. Production financial posting requires Accounting journal create/status/reversal APIs and compensating flows.
7. **Payments orchestration gap for fixed deposits and closures.** Fixed-deposit funding/payout and account-closure settlement are locally executed. If Payments owns orchestration, add explicit reserve/capture/settle contracts instead of bypassing it.
8. **Public mutation authorization granularity.** Internal routes are protected by service scopes, but holder, nominee, limit, mandate, and lifecycle mutations currently fall through to generic authentication rather than operation-specific scopes. Confirm and encode the intended scopes.
9. **OpenAPI drift.** The checked-in Deposit OpenAPI describes the older target contract and does not contain all 41 implemented endpoints. Regenerate a current provider OpenAPI from the running Spring application and contract-test it.
10. **Field naming.** CASA requests call the Product Master identifier `productId`; fixed-deposit requests call it `productCode`. A versioned migration to `productCode` will remove ambiguity.

## 11. Minimum production dependency set by capability

| Capability | Hard synchronous dependencies | Deposit provider APIs used by others |
|---|---|---|
| CASA eligibility/opening | CIF + Product Master | Public opening APIs |
| Holder or mandate addition | CIF | Public account-party APIs |
| Balance read | Accounting | Public balance API |
| Book transfer | Payments orchestrator + Accounting journal contract | Deposit eligibility, reserve, settle, release, status |
| Credit-card repayment | Payments + Accounting + Credit Card | Deposit eligibility, reserve, capture, release, status |
| Fixed-deposit quote | Product Master | Public quote API |
| Fixed-deposit booking | CIF + Product Master; production also Payments + Accounting | Public booking/read APIs |
| CASA closure | Production Payments + Accounting + Reconciliation clearance | Public and internal closure APIs |
| Fixed-deposit maturity/premature closure | Product Master policy snapshot; production Payments + Accounting | FD closure and EOD APIs |
| Statements | CIF ownership verification + Payments history; Deposit peer snapshot | Generic peer snapshot is still required |
| Daily close | EOD/Reconciliation orchestrator + Accounting | Deposit accrual/maturity/readiness APIs |
