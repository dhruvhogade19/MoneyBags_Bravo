# MoneyBags Payments Service

Beginner-friendly Spring Boot payment orchestrator for:

- internal Deposit Account book transfers;
- credit-card merchant payments using hold/capture/release;
- credit-card bill repayment from a Deposit Account;
- fixed-deposit funding, maturity payout and premature-closure payout;
- Accounting posting, timeout lookup and reversal;
- outcome notifications;
- Statements activity and basic EOD cutoff/drain controls.

Payments owns the payment lifecycle. Deposit owns deposit balances, Credit Card owns card limit and
outstanding, Accounting owns immutable journals, Billing owns bills, and Notification owns delivery.

Final payment outcomes are sent to Notification using `POST /internal/v1/notifications` with a
stable key in the form `payment-{paymentId}-{success|failed|reversed}`.

## Run immediately in demo mode

The repository default is the `oracle` profile. For an isolated demo, explicitly select `demo`; it
uses H2 and in-process fake peer services, so Oracle and the other microservices are not required.

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Open Swagger UI:

```text
http://localhost:8085/swagger-ui/index.html
```

Health endpoint:

```text
http://localhost:8085/actuator/health
```

## Swagger sample requests

Always give each new request a new `Idempotency-Key` header. Repeating the same key and body returns
the original payment. Reusing it with changed data returns `409 Conflict`.

### Book transfer

Endpoint: `POST /api/v1/payments/book-transfers`

Header: `Idempotency-Key: book-demo-001`

```json
{
  "requestorCustomerId": 101,
  "sourceAccountId": "dep-acc-001",
  "targetAccountId": "dep-acc-002",
  "amount": 500.00,
  "currencyCode": "INR",
  "reference": "Rent payment"
}
```

### Credit-card merchant payment

Endpoint: `POST /api/v1/payments/credit-card-payment/merchant-payment`

Header: `Idempotency-Key: merchant-demo-001`

```json
{
  "requestorCustomerId": 101,
  "creditCardAccountId": "CC-101",
  "merchantId": "MERCHANT-001",
  "amount": 50000.00,
  "currencyCode": "INR",
  "reference": "Merchant purchase"
}
```

### Credit-card repayment

Endpoint: `POST /api/v1/payments/credit-card-payment/repayment`

Header: `Idempotency-Key: repayment-demo-001`

```json
{
  "requestorCustomerId": 101,
  "billId": "BILL-202608-001",
  "sourceDepositAccountId": "dep-acc-001",
  "creditCardAccountId": "CC-101",
  "amount": 25000.00,
  "currencyCode": "INR",
  "reference": "Credit-card bill repayment"
}
```

Payments validates the repayment against Billing's current `outstandingAmount`. It accepts bills
in `GENERATED`, `PARTIALLY_PAID`, or `OVERDUE` state and rejects a `PAID` bill. After Deposit,
Accounting, and Credit Card settlement succeed, Payments calls:

```text
POST /internal/v1/bills/{billId}/payment-settlements
```

Payments becomes `SETTLED` only after Billing records that callback. A temporary callback failure
leaves the payment in `PENDING_BILLING`; retry it without repeating the financial operations via:

```text
POST /internal/v1/payments/{paymentId}/billing-settlement
```

Supply an `Idempotency-Key` header to the recovery endpoint.

### Queries

```text
GET /api/v1/payments/{paymentId}
GET /api/v1/payments/{paymentId}/history
GET /api/v1/payments?customerId=101&page=0&size=20
GET /internal/payments?accountId=dep-acc-001&from=2026-08-01&to=2026-08-31&page=0&size=100
```

Bank administrators use `/api/v1/payments/operations` to search by business date/status, cut off
or reopen new-payment intake, check EOD drain state, retry pending Accounting reversals, and retry
pending Billing callbacks. These endpoints require `payment:admin` or the `BANK_ADMIN` role.

### Fixed-deposit funding

Endpoint: `POST /api/v1/payments/fixed-deposit-funding`

Header: `Idempotency-Key: fd-funding-demo-001`

```json
{
  "requestorCustomerId": 101,
  "sourceAccountId": "dep-acc-001",
  "fixedDepositId": "fd-001",
  "amount": 100000.00,
  "currencyCode": "INR",
  "reference": "Initial funding for fd-001"
}
```

### Fixed-deposit maturity or premature payout

Endpoint: `POST /internal/v1/payments`

Header: `Idempotency-Key: fd-payout-demo-001`

```json
{
  "paymentType": "FIXED_DEPOSIT_MATURITY_PAYOUT",
  "requestorCustomerId": 101,
  "sourceAccountId": "fd-account-001",
  "destinationType": "DEPOSIT_ACCOUNT",
  "destinationAccountId": "dep-acc-001",
  "amount": 106968.00,
  "principalAmount": 100000.00,
  "interestAmount": 6968.00,
  "currencyCode": "INR",
  "reference": "FD maturity payout for fd-001",
  "fixedDepositId": "fd-001"
}
```

Use `FIXED_DEPOSIT_PREMATURE_PAYOUT` for early closure. Deposit supplies the authoritative
principal/interest breakdown; Payments validates that the two values add up to `amount`.

## Demo failure samples

- Deposit account ID containing `blocked` fails eligibility.
- Deposit account ID containing `insufficient` fails reservation.
- Merchant amount greater than `100000.00` fails card limit validation.
- Bill ID containing `missing` returns a missing-bill failure.
- Bill ID containing `callback-fail` leaves a completed repayment in `PENDING_BILLING` for recovery.
- A reference containing `accounting-fail` simulates an Accounting rejection.
- CIF ID `999` simulates Notification failure; the financial status remains settled.

## Connect to Oracle and real services

Create the Oracle user/schema if your DBA has not already created it:

```sql
CREATE USER MONEYBAGS_PAYMENT IDENTIFIED BY your_password;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER TO MONEYBAGS_PAYMENT;
ALTER USER MONEYBAGS_PAYMENT QUOTA UNLIMITED ON USERS;
```

The root `.env` file is the preferred place for local credentials. Payments uses the shared
`MONEYBAGS_DB_*` values, with `PAYMENTS_DB_*` retained as optional standalone fallbacks:

```powershell
$env:SPRING_PROFILES_ACTIVE = "oracle"
$env:MONEYBAGS_DB_URL = "jdbc:oracle:thin:@//localhost:1522/FREEPDB1"
$env:MONEYBAGS_DB_USERNAME = "MONEYBAGS_PAYMENT"
$env:MONEYBAGS_DB_PASSWORD = "your-password"

$env:DEPOSIT_BASE_URL = "http://localhost:8086"
$env:CREDIT_CARD_BASE_URL = "http://localhost:8084"
$env:BILLING_BASE_URL = "http://localhost:8087"
$env:ACCOUNTING_BASE_URL = "http://localhost:8088"
$env:ACCOUNTING_SERVICE_TOKEN = "service-token"
$env:NOTIFICATION_BASE_URL = "http://localhost:8090"
$env:EUREKA_ENABLED = "true"

mvn spring-boot:run
```

Liquibase creates `PAYMENT`, `PAYMENT_STATUS_HISTORY`, and `PAYMENT_ATTEMPT`. Hibernate validates the
Oracle schema instead of changing it automatically.

## Tests

```powershell
mvn test
```

The tests use H2 and demo peer clients.
