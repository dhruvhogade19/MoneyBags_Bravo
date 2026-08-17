# Bill Generation Service — Process and Operations Guide

## Purpose and ownership

Bill Generation Service runs on port `8087` and owns the credit-card billing domain in the `MONEYBAGS_BILLING` Oracle schema. It creates immutable bill snapshots for an account and billing period, tracks payments allocated to those bills, records bill-status history, and answers whether an account can be closed.

It does not own credit-card accounts, product/rate definitions, accounting journals, payment orchestration, or delivery of notifications. Those concerns remain with their owning services.

## Service process

### 1. Generate a bill

`POST /internal/v1/bills/generate` accepts an account ID, billing period (`YYYY-MM`), and business date. An `Idempotency-Key` header is required.

1. The service validates the period and idempotency key.
2. It rejects a reused key with different request data, and prevents more than one bill for the same account and billing period.
3. It obtains the credit-card account, current product/rate information, and ledger activity for the billing period. In local stub mode, deterministic fixture data is used instead.
4. The account must be `ACTIVE`.
5. It calculates the opening balance from the previous bill (or the card outstanding balance for the first bill), applies activities, calculates monthly interest, and adds the configured late-payment fee when the preceding bill is overdue.
6. The service calculates the minimum due as the greater of the configured percentage and fixed minimum, capped at the total due.
7. It saves the bill, bill lines, product/rate/fee snapshot, initial `GENERATED` status history, idempotency record, and audit record.
8. It posts calculated interest and fee components to Accounting, then requests a `BILL_GENERATED` notification for the cardholder CIF.

The generated bill includes `previousBalance`, `totalAmountDue`, `minimumAmountDue`, `paidAmount`, `outstandingAmount`, `paymentDueDate`, and all statement lines. Monetary amounts are kept at four decimal places; the notification presents total amount to two decimal places.

### 2. Record a payment settlement

After Accounting posts a successful card-payment journal, Payments calls:

`POST /internal/v1/bills/{billId}/payment-settlements`

The request needs `paymentId`, `journalNumber`, positive `amount`, three-letter uppercase `currency`, and `settledAt`.

`paymentId` is the idempotency key for settlement. A repeated identical allocation returns the existing bill summary. Reusing it for a different bill, amount, or journal results in a conflict. Settlement currency must match the bill, and overpayment is rejected. A successful partial payment sets the bill to `PARTIALLY_PAID`; paying the full total sets it to `PAID`.

### 3. End-of-day close

`POST /internal/v1/bills/eod/close` requires an `Idempotency-Key` header plus `eodRunId`, `businessDate`, and `commandReference`.

It marks bills with a due date before the business date as `OVERDUE` when their current status is `GENERATED` or `PARTIALLY_PAID`, and writes a `PAYMENT_DUE_DATE_PASSED` history record. The response reports the number of bills generated for the business date and any pending references.

### 4. Account closure check

Credit Card Service can call:

`GET /internal/v1/bills/accounts/{accountId}/closure-eligibility`

An account is eligible only when every bill is `PAID`. The response includes any bill IDs blocking closure.

## API reference

| Endpoint | Consumer / purpose | Key requirements |
|---|---|---|
| `POST /internal/v1/bills/generate` | Scheduled process or internal caller creates a statement | `Idempotency-Key`; `accountId`, `billingPeriod`, `businessDate` |
| `GET /api/v1/bills/{billId}` | Retrieve a bill and lines | Bill ID |
| `GET /internal/v1/bills/{billId}/summary` | Retrieve payment-facing bill totals | Bill ID |
| `GET /internal/v1/bills` | Search bills | Optional `accountId`, `billingPeriod`, `status`; `page` >= 0; `size` 1–100 |
| `POST /internal/v1/bills/{billId}/payment-settlements` | Payments reports settled payment | Settlement body described above |
| `GET /internal/v1/bills/accounts/{accountId}/closure-eligibility` | Credit Card checks closure constraints | Account ID |
| `POST /internal/v1/bills/eod/close` | EOD process marks overdue bills | `Idempotency-Key`; EOD request body |
| `GET /actuator/health` | Health probe | None |
| `GET /swagger-ui.html` | Interactive API documentation | None |

Error responses use `code`, `message`, `status`, and `correlationId`. Common outcomes include `INVALID_BILLING_PERIOD`, `IDEMPOTENCY_CONFLICT`, `BILL_ALREADY_EXISTS`, `CARD_NOT_ACTIVE`, `BILL_NOT_FOUND`, `CURRENCY_MISMATCH`, `PAYMENT_SETTLEMENT_CONFLICT`, and `OVERPAYMENT_NOT_SUPPORTED`.

## Integration contracts

When `STUB_UPSTREAM_CLIENTS=false`, Bill Generation reads:

| Owner | Request made by Bill Generation | Required information |
|---|---|---|
| Credit Card | `GET /internal/v1/credit-card-accounts/{accountId}` | `cifId`, `productCode`, `status`, `outstandingAmount` |
| Credit Card | `GET /api/credit-cards/accounts/{accountId}/interest-rate` | `purchaseInterestRate` |
| Product Master | `GET /api/v1/products/{productCode}/pricing` | Currency, interest policy, minimum-payment rules, due days, active fees |
| Accounting | `GET /internal/v1/ledger-entries` for the account/date range | Ledger entry type, reference, description, amount |

It sends:

| Owner | Request | Payload purpose |
|---|---|---|
| Accounting | `POST /internal/v1/bill-postings` | Interest and fee components calculated for a bill |
| Notification | `POST /internal/v1/notifications` | A `BILL_GENERATED` message with bill period, amount, currency, and due date; idempotency key is `bill-{billId}-generated` |

Payments is expected to call the payment-settlement endpoint only after its Accounting journal succeeds.

## Data and migrations

Liquibase is the only schema-change mechanism. The master changelog is `src/main/resources/db/changelog/db.changelog-master.yaml`.

| Table | Service-owned data |
|---|---|
| `BILL` | Statement totals, dates, currency, status, and paid amount |
| `BILL_LINE` | Immutable statement components and source references |
| `BILL_CALCULATION_SNAPSHOT` | Product/rate/fee rules used for the calculation |
| `BILL_STATUS_HISTORY` | Lifecycle transitions and reasons |
| `BILL_PAYMENT_ALLOCATION` | Payment-to-bill allocations; unique by payment ID |
| `IDEMPOTENCY_RECORD` | Hashed generation idempotency keys and request data |
| `AUDIT_LOG` | Successful bill-generation and settlement audit events |
| `BILLING_CYCLE` | Billing-cycle reference data |

Do not let another service query or modify these tables. Integrate through the endpoints above.

## Configuration and local operation

Copy the repository `.env.example` to a local `.env` and set the shared Oracle and Eureka values. The service reads `.env` from either the repository root or the module directory.

| Setting | Default | Use |
|---|---|---|
| `MONEYBAGS_DB_URL`, `MONEYBAGS_DB_USERNAME`, `MONEYBAGS_DB_PASSWORD` | Falls back to legacy `DB_*` values | Oracle connection |
| `EUREKA_ENABLED` | `false` | Set `true` for discovery registration |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka endpoint |
| `SECURITY_ENABLED` | `false` | Enables the service security configuration |
| `STUB_UPSTREAM_CLIENTS` | `true` | Uses deterministic local upstream fixture data |
| `STUB_NOTIFICATION_CLIENT` | Same value as upstream stub flag | Suppresses actual notification delivery when true |
| `PRODUCT_URL`, `CREDIT_CARD_URL`, `ACCOUNTING_URL` | Code defaults | Base URLs for live upstream calls; set these explicitly per environment |
| `NOTIFICATION_URL` | `http://localhost:8090` | Notification Service base URL |
| `LIQUIBASE_CONTEXTS` | `testdata` | Includes the test-data changeset when set to `testdata` |

The current code defaults for Product, Credit Card, and Accounting URLs should not be relied on for a full environment. Set all three explicitly to the deployed owners’ URLs; this avoids coupling to stale local port assumptions.

Run locally from the repository root:

```powershell
mvn -pl bill-generation-service -am spring-boot:run
```

Run tests:

```powershell
mvn -pl bill-generation-service -am test
```

The Postman collection is at `postman/Bill-Generation-Service.postman_collection.json`. With stub upstream clients enabled, it can exercise bill generation without starting peer services.

## Operational checks

Before deploying or handing off a change:

- Confirm Liquibase applies against a clean Oracle schema and Hibernate schema validation succeeds.
- Verify `/actuator/health` and `/swagger-ui.html` directly on port `8087`.
- Test generation success, duplicate bill, repeated idempotency key, changed request with the same key, inactive card, payment partial/full/repeat/conflict, and closure eligibility.
- With live integrations enabled, test Accounting postings and Notification delivery using non-production test data.
- Verify Eureka registration and the narrow gateway route when running in the full environment.
- Do not change owned upstream data directly or add cross-service database dependencies.
