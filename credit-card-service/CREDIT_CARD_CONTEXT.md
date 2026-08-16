# Credit Card Service: Current Architecture and Context

This document is the persistent context for the `credit-card-service` module. It describes the current implementation and agreed design as represented by the code, Liquibase migrations, configuration, tests, and exposed controller APIs.

## 1. Service scope

Credit Card Service owns the credit-card domain for:

- credit-card application submission and eligibility decision;
- creation of one credit-card account per approved application;
- card-number generation, sanctioned-limit, available-limit, and outstanding-credit tracking;
- the authoritative `HOLD -> CAPTURE / RELEASE` spending flow;
- bill-payment balance updates;
- account-opening and account-closure lifecycle coordination with Accounting;
- successful-account-opening notification coordination with Notification Service; and
- EOD readiness checks over its own application and account state.

The service runs on port `8084`. Swagger UI is configured at `/swagger-ui.html`.

## 2. Ownership boundaries

| Service/domain | Ownership and responsibilities |
|---|---|
| Credit Card Service | Owns the three credit-card tables, application/account/hold state, card-number generation, credit-limit balances, hold transitions, and local account status. It constructs Accounting and Notification requests from existing data but does not persist their payload fields. |
| Payment Service | Calls the hold, capture, release, and bill-payment APIs. It supplies the unique payment `referenceId` for holds and decides whether a held payment succeeds (`capture`) or fails/cancels (`release`). |
| Product Master | Evaluates credit-card eligibility and pricing. Credit Card Service sends application/CIF facts and uses only `eligible` plus `applicableInterestRule.annualInterestRate`. |
| CIF/KYC | Owns customer facts returned to this service: CIF ID, employment type, salary, age, and KYC status. Credit Card Service stores snapshots of age, salary, and KYC status at application time. |
| Accounting | Owns accounting lifecycle confirmation and clearance. It is authoritative for `OPEN` and `CLOSED` lifecycle responses and may recheck balances during final closure. |
| Bill Generation | Owns bill composition, including interest, fees, and other charges that are not stored as credit-card outstanding amount. It is not currently called by this module. |
| Notification Service | Owns delivery of the `CREDIT_CARD_CREATED` notification. It does not control account lifecycle and notification failure does not reverse a successful account opening. |
| Explicitly outside Credit Card Service | Authentication/authorisation, statement generation, interest/fee accrual, ledger posting, settlement, payment-operation orchestration, customer notification delivery state, and persistent Accounting event storage. |

## 3. Database tables

Liquibase runs `V1__create_credit_card_schema.sql`, `V2__create_credit_card_hold.sql`, then `V3__add_cif_snapshots.sql`. There are exactly three Credit Card Service tables.

### `CREDIT_CARD_APPLICATION`

Purpose: stores the submitted application, CIF snapshots, Product Master decision, pricing snapshot, and application state.

| Column | Notes |
|---|---|
| `APPLICATION_ID` | `Long` identity primary key. |
| `CIF_ID` | Required `Long` CIF identifier. |
| `PRODUCT_CODE` | Required product code. |
| `REQUESTED_CREDIT_LIMIT` | Required positive `NUMBER(19,2)`. |
| `APPROVED_CREDIT_LIMIT` | Nullable until an application is approved; for the automatic decision flow it is the requested limit when eligible. |
| `PURCHASE_INTEREST_RATE_SNAPSHOT` | `NUMBER(9,4)`; copied from Product Master `applicableInterestRule.annualInterestRate` only for an eligible application. |
| `APPLICATION_STATUS` | Required string enum: `PENDING`, `APPROVED`, `REJECTED`. |
| `KYC_STATUS_SNAPSHOT` | Raw CIF `kycStatus` string, for example `APPROVED`, `REJECTED`, or `PENDING`. |
| `AGE` | CIF age snapshot (`NUMBER(3)`). |
| `SALARY` | CIF salary snapshot (`NUMBER(19,2)`). |
| `ELIGIBILITY_STATUS` | Required string enum: `PENDING`, `ELIGIBLE`, `NOT_ELIGIBLE`. |
| `SUBMITTED_AT` | Required timestamp with time zone. |
| `UPDATED_AT` | Required timestamp with time zone. |

`CIF_ID` is indexed. `REQUESTED_CREDIT_LIMIT > 0` is enforced by a database check constraint.

### `CREDIT_CARD_ACCOUNT`

Purpose: stores one account per application and the service-owned credit state.

| Column | Notes |
|---|---|
| `ACCOUNT_ID` | `Long` identity primary key. |
| `APPLICATION_ID` | Required, unique application reference and foreign key to `CREDIT_CARD_APPLICATION`. |
| `CIF_ID` | Required `Long` CIF identifier. |
| `PRODUCT_CODE` | Required product code. |
| `AGE` | Application/CIF snapshot. |
| `SALARY` | Application/CIF snapshot. |
| `CARD_NUMBER` | Required, unique, plain 16-digit card number: `4000` followed by 12 random digits. It is intentionally stored and returned in plain form in the current design. |
| `SANCTIONED_LIMIT` | Required approved credit limit. |
| `PURCHASE_INTEREST_RATE_SNAPSHOT` | Interest-rate snapshot inherited from the application. |
| `AVAILABLE_LIMIT` | Required currently usable credit. |
| `OUTSTANDING_AMOUNT` | Required credit amount currently used by the customer. It does not include bill-generation interest, fees, or other external bill charges. |
| `STATUS` | Required string enum: `ACTIVE`, `BLOCKED`, `CLOSURE_PENDING`, `CLOSED`. |
| `OPENED_AT` | Required timestamp with time zone; created in UTC. |

`CIF_ID` is indexed. Database checks ensure non-negative available/outstanding amounts and `AVAILABLE_LIMIT <= SANCTIONED_LIMIT`.

### `CREDIT_CARD_HOLD`

Purpose: stores an idempotent reservation of credit for a Payment Service operation.

| Column | Notes |
|---|---|
| `HOLD_ID` | `Long` identity primary key. |
| `ACCOUNT_ID` | Required account foreign key. |
| `REFERENCE_ID` | Required, globally unique Payment Service reference. |
| `AMOUNT` | Required positive `NUMBER(19,2)`. |
| `STATUS` | Required enum: `HELD`, `CAPTURED`, `RELEASED`. |
| `CREATED_AT` | Required timestamp with time zone. |

There is an account index, a unique constraint on `REFERENCE_ID`, and a database status/positive-amount check. There are no additional tables in this module.

## 4. Application flow

Normal submitted-application flow:

1. Client submits `cifId`, `productCode`, and `requestedCreditLimit`.
2. Credit Card Service calls CIF/KYC for customer facts and snapshots `kycStatus`, `age`, and `salary` into the application.
3. It calls Product Master to validate the application.
4. If `eligible` is `false`, it saves the application with `APPLICATION_STATUS = REJECTED`, `ELIGIBILITY_STATUS = NOT_ELIGIBLE`, and null approved limit/rate. No account is created.
5. If `eligible` is `true`, `applicableInterestRule.annualInterestRate` is required. It saves the application with `APPLICATION_STATUS = APPROVED`, `ELIGIBILITY_STATUS = ELIGIBLE`, `APPROVED_CREDIT_LIMIT = REQUESTED_CREDIT_LIMIT`, and the interest-rate snapshot.
6. It creates the account in the same transaction flow. Each approved application has exactly one account (`APPLICATION_ID` is unique on the account table).

The controller still exposes manual `approve` and `reject` endpoints for records that are `PENDING`; the current normal submission path decides immediately and therefore does not create new pending applications.

## 5. Account lifecycle

Current account statuses are `ACTIVE`, `BLOCKED`, `CLOSURE_PENDING`, and `CLOSED`.

### Opening

1. The service creates/saves an account as `BLOCKED`, with sanctioned and available limit equal to the approved limit and outstanding amount zero.
2. It generates `accountReference = "CC-" + accountId`.
3. It calls Accounting with a `CREDIT_CARD_ACCOUNT_OPENED` lifecycle event.
4. Only when Accounting responds `accountingLifecycleState = "OPEN"` does the service set local status to `ACTIVE`.
5. After the account has been set `ACTIVE`, it sends the `CREDIT_CARD_CREATED` notification. Notification failure is logged and does not change the account back from `ACTIVE`.

If Accounting does not return `OPEN`, the account remains `BLOCKED` and no creation notification is sent.

### Closure

1. `POST /accounts/{accountId}/close` obtains the existing pessimistic account lock.
2. `CLOSED` cannot be closed again. `ACTIVE`, `BLOCKED`, and `CLOSURE_PENDING` can enter/retry closure.
3. The service sets status to `CLOSURE_PENDING`. New holds are blocked because only `ACTIVE` accounts can create holds.
4. It calls Accounting clearance for `CC-{accountId}`.
5. If `accountingCleared` is false, it returns with `CLOSURE_PENDING`; it does not call final close.
6. If cleared, it posts `CREDIT_CARD_ACCOUNT_CLOSED` to Accounting.
7. Only Accounting response `accountingLifecycleState = "CLOSED"` sets the local account to `CLOSED`.

The clearance GET is preliminary only. Accounting final close is authoritative and may recheck balance. There is deliberately no account-closure notification.

## 6. Authoritative hold/capture/release flow

Direct spending is not an authoritative flow. Payment processing is:

```text
HOLD -> CAPTURE when payment posting succeeds
     -> RELEASE when payment fails or is cancelled
```

All three operations are transactional and call `CreditCardAccountRepository.lockById()` with `PESSIMISTIC_WRITE`; concurrent operations on one account serialize.

- **HOLD**: locks the account, requires `ACTIVE`, checks `AVAILABLE_LIMIT >= amount`, then subtracts the amount from `AVAILABLE_LIMIT`. It does not change `OUTSTANDING_AMOUNT`. A pre-existing `REFERENCE_ID` for the same account returns the existing hold and does not reserve again. The same reference on another account is a conflict.
- **CAPTURE**: locks the account and validates the hold belongs to it. `HELD -> CAPTURED` adds the hold amount to `OUTSTANDING_AMOUNT`; it does not subtract available limit again. A repeated capture is idempotent. A released hold cannot be captured.
- **RELEASE**: locks the account and validates ownership. `HELD -> RELEASED` adds the hold amount back to `AVAILABLE_LIMIT`; it does not change outstanding. A repeated release is idempotent. A captured hold cannot be released.

Valid hold transitions are `HELD -> CAPTURED` and `HELD -> RELEASED`; terminal states do not transition to the other terminal state.

`GET /available-limit` is deliberately a read-only view and is not used for concurrency control.

## 7. Bill payment behavior

`POST /api/credit-cards/accounts/{accountId}/payments/billpaid` locks the account and requires a positive payment amount.

```text
amountApplied = min(paymentAmount, outstandingAmount)
OUTSTANDING_AMOUNT -= amountApplied
AVAILABLE_LIMIT += amountApplied
```

Only the credit amount maintained by this service is applied. Bill Generation may charge interest, fees, or other amounts above `OUTSTANDING_AMOUNT`; Credit Card Service ignores that excess portion rather than rejecting the payment or increasing available limit above its sanctioned limit.

## 8. Exposed API surface

Base path: `/api/credit-cards`. No authentication/authorisation is implemented at present. Swagger labels intended consumers but does not enforce roles.

| Method and path | Intended consumer | Purpose | Request / response |
|---|---|---|---|
| `POST /applications` | Customer/channel | Submit and decide a credit-card application. | `ApplicationRequest { cifId, productCode, requestedCreditLimit }` -> `ApplicationResponse` (`201`). |
| `GET /applications/{applicationId}` | Customer/admin | Read an application. | No body -> `ApplicationResponse`. |
| `GET /applications/cif/{cifId}` | Customer/admin | List applications for a CIF. | No body -> list of `ApplicationResponse`. |
| `POST /applications/{applicationId}/approve` | Admin (intended) | Approve a pending eligible application and create its account. | No body -> `AccountResponse`. |
| `POST /applications/{applicationId}/reject` | Admin (intended) | Reject a pending application. | No body -> `ApplicationResponse`. |
| `POST /accounts` | Admin/system (intended) | Create an account from an approved application. | `AccountCreateRequest { applicationId }` -> `AccountResponse` (`201`). |
| `GET /accounts/{accountId}` | Customer/admin | Read account detail. | No body -> `AccountResponse`. |
| `GET /accounts/cif/{cifId}` | Customer/admin | List accounts for a CIF. | No body -> list of `AccountResponse`. |
| `GET /accounts/{accountId}/available-limit` | Customer/admin | Read-only available-credit view. | No body -> `LimitResponse`. |
| `GET /accounts/{accountId}/interest-rate` | Customer/admin | Read account interest-rate snapshot. | No body -> `InterestRateResponse`. |
| `POST /accounts/{accountId}/close` | Customer/admin (intended) | Start/retry Accounting-backed account closure. | No body -> `AccountResponse` (`CLOSURE_PENDING` or `CLOSED`). |
| `POST /accounts/{accountId}/holds` | Payment Service | Atomically reserve credit. | `HoldRequest { referenceId, amount }` -> `HoldResponse` (`201`). |
| `POST /accounts/{accountId}/holds/{holdId}/capture` | Payment Service | Capture a held payment. | No body -> `HoldResponse`. |
| `POST /accounts/{accountId}/holds/{holdId}/release` | Payment Service | Release a held payment. | No body -> `HoldResponse`. |
| `POST /accounts/{accountId}/payments/billpaid` | Payment Service | Apply bill payment to current credit outstanding. | `AmountRequest { amount }` -> `AccountResponse`. |
| `GET /accounts/eod/readiness` | EOD/operations | Check local credit-card EOD readiness. | No body -> `EodReadinessResponse`. |
| `GET /internal/v1/credit-card-accounts/{accountId}/billing-details` | Trusted billing consumer | Read billing-relevant account details. | No body -> `BillingAccountDetails { accountId, cifId, productCode, purchaseInterestRate, outstandingAmount, status }`. |

`400` is used for validation/bad input, `404` for missing application/account/hold, and `409` for invalid state/credit conflicts where applicable. Error responses are `{ "message": "..." }`.

## 9. External integration contracts

External clients are RestClient implementations when `STUB_UPSTREAM_CLIENTS=false`; local stub implementations are active by default.

### CIF/KYC

```text
GET /api/v1/cifs/{id}/credit-card-details
```

Expected response fields:

```json
{
  "cifId": 101,
  "employmentType": "SALARIED",
  "salary": 75000.00,
  "age": 30,
  "kycStatus": "APPROVED"
}
```

Employment type values agreed for CIF are `BUSINESS`, `SALARIED`, and `STUDENT`. KYC is stored as the raw `kycStatus` snapshot. For Product Master, only `APPROVED` maps to `kycCompleted: true`; `REJECTED` and `PENDING` map to `false`.

### Product Master

```text
POST /internal/v1/products/{productCode}/validate-credit-card-application
```

Request sent by Credit Card Service:

```json
{
  "requestedCreditLimit": 100000.00,
  "age": 30,
  "monthlyIncome": 75000.00,
  "employmentType": "SALARIED",
  "kycCompleted": true
}
```

The implementation consumes:

```json
{
  "eligible": true,
  "applicableInterestRule": {
    "annualInterestRate": 42.00
  }
}
```

Additional Product Master fields, such as validation messages, fees, and credit-card policy details, are not stored or used by this module.

### Payment Service

Payment Service calls the four internal operations listed in the API table: hold, capture, release, and bill payment. Representative payloads:

```json
// POST /accounts/{accountId}/holds
{ "referenceId": "PAY-12345", "amount": 50000.00 }

// POST /accounts/{accountId}/payments/billpaid
{ "amount": 25000.00 }
```

Capture and release have no request body. Hold response fields are `holdId`, `accountId`, `referenceId`, `amount`, `status`, and `createdAt`.

### Accounting

Configured base URL: `ACCOUNTING_URL` (default `http://localhost:8088`). Currency is always `INR`.

Opening event:

```text
POST /internal/v1/account-lifecycle-events
```

```json
{
  "eventReference": "CARD-OPEN:CC-5001",
  "eventType": "CREDIT_CARD_ACCOUNT_OPENED",
  "accountType": "CREDIT_CARD_ACCOUNT",
  "accountReference": "CC-5001",
  "productCode": "CARD-GOLD",
  "currencyCode": "INR",
  "businessDate": "2026-08-16",
  "occurredAt": "2026-08-16T10:00:00Z"
}
```

Closure clearance:

```text
GET /internal/v1/account-clearances/CREDIT_CARD_ACCOUNT/{accountReference}?currencyCode=INR
```

```json
{ "accountingCleared": true, "blockers": [] }
```

Final closure event:

```text
POST /internal/v1/account-lifecycle-events
```

```json
{
  "eventReference": "CARD-CLOSE:CC-5001",
  "eventType": "CREDIT_CARD_ACCOUNT_CLOSED",
  "accountType": "CREDIT_CARD_ACCOUNT",
  "accountReference": "CC-5001",
  "currencyCode": "INR",
  "businessDate": "2026-08-16",
  "occurredAt": "2026-08-16T15:00:00Z",
  "reasonCode": "CUSTOMER_REQUEST"
}
```

Lifecycle response used by the service:

```json
{ "accountingLifecycleState": "OPEN" }
```

or, for closure:

```json
{ "accountingLifecycleState": "CLOSED" }
```

### Notification Service

Configured base URL: `NOTIFICATION_URL` (default `http://localhost:8090`). It is called only after a successful Accounting `OPEN` response and local status change to `ACTIVE`.

```text
POST /internal/v1/notifications
```

```json
{
  "cifId": 101,
  "notificationType": "CREDIT_CARD_CREATED",
  "sourceReference": "CC-5001",
  "templateVariables": {
    "accountId": "CC-5001",
    "cardLastFour": "1234"
  }
}
```

No closure notification exists.

### EOD

EOD is currently a local Credit Card Service read operation, not an outbound peer call:

```text
GET /api/credit-cards/accounts/eod/readiness
```

It reports `readyForEod`, active/blocked account counts, pending-application count, and `closureBlockers`. It checks for approved applications without accounts, invalid status values, and inconsistent available/outstanding credit state.

## 10. Idempotency and correlation

| Flow | Idempotency behavior | Correlation behavior |
|---|---|---|
| Payment hold | `REFERENCE_ID` is a unique `CREDIT_CARD_HOLD` value. Retrying the same reference for the same account returns the existing hold without changing balances. | No inbound header is currently required or consumed by the controller. |
| Accounting opening | `Idempotency-Key: CARD-OPEN:CC-{accountId}`. Event reference is the same value. | `X-Correlation-Id` is propagated from the current HTTP request when present; otherwise the RestClient gateway generates a UUID. |
| Accounting final closure | `Idempotency-Key: CARD-CLOSE:CC-{accountId}`. Event reference is the same value. | Same request-header propagation/generation behavior. |
| Accounting clearance | No idempotency key because it is a GET. | Same request-header propagation/generation behavior. |
| Account-created notification | `Idempotency-Key: credit-card-CC-{accountId}-created`. | Same request-header propagation/generation behavior. |

## 11. Important implementation decisions

- Each approved application creates its own credit-card account. `APPLICATION_ID` is unique in `CREDIT_CARD_ACCOUNT`.
- `HOLD -> CAPTURE / RELEASE` is the only authoritative credit-spending flow. Do not reintroduce direct available-limit/outstanding updates as a payment-spend alternative.
- Available-limit GET is read-only and is not a concurrency-control API.
- Account locks use JPA pessimistic write locking (`lockById`) for hold, capture, release, bill payment, and closure.
- Card numbers use `4000` plus 12 random digits; current design intentionally stores and returns the plain number.
- CIF uses `Long` CIF IDs. Salary/age/KYC are snapshots, not auto-refreshed account values.
- Product Master expects `monthlyIncome` and boolean `kycCompleted`, despite Credit Card Service storing salary as `SALARY` and raw KYC as `KYC_STATUS_SNAPSHOT`.
- Accounting currency is fixed to `INR`.
- Accounting event/clearance payload fields are integration DTOs only; do not duplicate them into Credit Card Service tables unless a field becomes genuinely owned by this service.
- Notification failure must never undo an Accounting-confirmed `ACTIVE` account. There is no closure notification.
- No authentication/authorisation implementation exists yet; OpenAPI only describes intended consumers/roles.

## 12. Current implementation status

### Implemented

- Application validation, automatic approve/reject decision, rate/limit snapshots, and account creation.
- `CREDIT_CARD_APPLICATION`, `CREDIT_CARD_ACCOUNT`, and `CREDIT_CARD_HOLD` Liquibase schema.
- Long identity IDs for applications, accounts, and holds.
- Plain `4000`-prefixed generated card numbers.
- Pessimistically locked hold/capture/release and idempotent holds.
- Read-only available-limit endpoint.
- Bill-payment `min(paymentAmount, outstandingAmount)` rule and positive-amount validation.
- Accounting opening, clearance, and final-close integrations with local status gating.
- Successful-open account-created Notification integration with best-effort failure handling.
- EOD readiness endpoint, Swagger/OpenAPI documentation, Postman hold/bill/close requests, and focused unit tests.

### Contract agreed but not yet implemented

- Bill Generation integration and any endpoint/job that accrues or posts interest, fees, or other bill charges into a bill. Those charges are intentionally outside current `OUTSTANDING_AMOUNT` handling.
- Durable retry/outbox handling for Accounting or Notification calls. Current calls are synchronous; notification failure is logged and not retried by this service.
- A separate account-opening retry/reconciliation workflow for accounts left `BLOCKED` when Accounting does not return `OPEN`.

### Intentionally out of scope

- Authentication and authorisation enforcement.
- Direct credit-spending API/flow outside HOLD -> CAPTURE / RELEASE.
- Persisting Accounting lifecycle-event or clearance fields in Credit Card Service tables.
- Notification delivery state or account-closure notifications.
- Product pricing, fee calculation, statement/bill generation, interest accrual, accounting ledger posting, and payment orchestration.
