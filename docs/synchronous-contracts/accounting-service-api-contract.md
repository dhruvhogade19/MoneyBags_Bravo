# Accounting Service - API Contract and Implementation Scope

Contract revision: 2.3  
Technology baseline: Java 25, Spring Boot 4.1.0, Spring Cloud 2025.1.2, Oracle, Liquibase, Maven  
Communication model: synchronous REST for the current release

## Service URLs

- Direct service URL: `http://localhost:8088`
- API Gateway URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8088/swagger-ui.html`
- Eureka service name: `ACCOUNTING-SERVICE`
- Oracle schema/user: `MONEYBAGS_ACCOUNTING`

The gateway exposes only the Accounting administration and inquiry APIs under `/api/v1/**`. Trusted peer APIs under `/internal/v1/**` are called directly by other services and must not be exposed through the public gateway.

## Purpose

Accounting is the authoritative double-entry financial record for Moneybags. Payments, Bill Generation, and other source services perform business operations; Accounting converts the accepted financial facts into immutable and balanced journals.

Every successful journal must satisfy:

```text
totalDebit = totalCredit
```

Posted journals are never edited or deleted. A correction is represented by a new journal containing the opposite debit and credit entries.

Accounting records financial effects but does not move money or change operational account balances. Deposit Account and Credit Card own those balance projections, while Payments owns the overall payment lifecycle.

## Ownership boundary

Accounting owns:

- GL accounts and their status.
- Effective-dated accounting-rule versions.
- Effective-dated subledger-to-GL mappings.
- Financial posting requests, request hashes, and posting outcomes.
- Immutable journals, journal lines, and posting sequence.
- Immutable journal reversals.
- Journal-derived account balances and ledger-entry inquiries.
- An Accounting projection of Deposit and Credit Card account lifecycle events.
- Accounting clearance checks used before an account-owning service closes an account.
- Trial-balance runs and lines.
- Payments-to-Accounting reconciliation runs and exceptions.
- Accounting business-date periods.
- Accounting audit records.

Accounting does not own:

- Payment lifecycle or payment status.
- Deposit balances, reservations, or settlement operations.
- Credit-card limits, outstanding projections, or repayment allocation.
- The authoritative operational status or final closure decision for a Deposit or Credit Card account.
- Customer/CIF, KYC, or personal information.
- Product definitions or pricing calculations.
- Bills, statements, notifications, or EOD orchestration.

Peer services must never read the Accounting database directly.

## Dependency direction

The core posting engine has no runtime REST dependency on Deposit Account, Credit Card, Product Master, CIF, KYC, Statements, Notification, or Loan services. Source services send complete financial facts with opaque account references.

```text
Payments --------POST payment fact--------> Accounting
Bill Generation -POST calculated charges--> Accounting
EOD -------------POST control commands----> Accounting
Deposit Account -POST FD financial fact----> Accounting
Deposit Account -POST account lifecycle----> Accounting
Credit Card -----POST account lifecycle----> Accounting

Deposit Account --GET journal balance------> Accounting
Deposit/Card ----GET accounting clearance--> Accounting
Bill Generation --GET ledger activity------> Accounting
```

Accounting therefore does not require a `RestClient` for normal posting. If a future detailed reconciliation flow needs Accounting to read a peer service, it will use a load-balanced Spring `RestClient` with Eureka resolution, bounded timeouts, correlation propagation, and retry only for idempotent operations.

## Functional scope

### Core ledger scope

- Receive book-transfer, credit-card-repayment, and future merchant-payment accounting requests from Payments.
- Receive completed refund requests linked to an original Accounting journal.
- Receive bill-generated interest, fee, penalty, and tax components from Bill Generation.
- Resolve effective-dated accounting rules and mappings.
- Create immutable double-entry journals.
- Prevent duplicate posting through stable external references and canonical request hashes.
- Return authoritative journal details and posting outcomes.
- Resolve an uncertain posting outcome after a caller timeout.
- Reverse a posted journal without modifying the original.
- Provide journal-derived balances and ledger-entry activity.
- Maintain and audit GL accounts, accounting rules, and mappings.
- Receive Fixed Deposit funding, interest, payout, maturity, and premature-closure facts calculated and orchestrated by Deposit Account.
- Register Deposit and Credit Card account opening and closing lifecycle facts without creating a journal.
- Report whether Accounting's balances are clear before the owning service closes an account.
- Recheck Accounting clearance atomically when accepting a final account-closing event.
- Reject ordinary financial postings against an account already marked closed in Accounting.
- Reject postings for a closed accounting period.

### EOD control scope

- Generate and persist trial balances.
- Reconcile Payments control totals supplied by EOD.
- Track and resolve reconciliation exceptions.
- Open and close Accounting periods.
- Block period closure when the trial balance is unbalanced or a blocking reconciliation item remains open.

## Required headers and common behaviour

Every mutable API requires:

```http
Idempotency-Key: stable-command-key
X-Correlation-Id: end-to-end-trace-id
Authorization: Bearer <service-or-user-token>
```

Administrative concurrent updates also require:

```http
If-Match: <current-version>
```

Idempotency behaviour:

- Same key/reference and identical canonical request: return the original result with `idempotentReplay=true`.
- Same key/reference with changed content: return `409 Conflict`.
- A caller timeout does not imply failure; the caller first uses the outcome-lookup API.
- Cross-service identifiers are opaque strings and are not required to be UUIDs.
- Money uses decimal values and an ISO-4217 currency code.
- Dates and timestamps use UTC ISO-8601 formats.
- Errors use `application/problem+json` and include a stable `code` and `correlationId`.

## Exposed API inventory

### Payment posting APIs

| Consumer | Method and API | Purpose |
|---|---|---|
| Payments | `POST /internal/v1/payment-postings/settlements` | Post the Accounting leg for a book transfer, credit-card repayment, or future merchant payment. |
| Payments | `POST /internal/v1/payment-postings/refunds` | Post a full or partial refund derived from an original journal. |
| Payments | `GET /internal/v1/payment-postings/by-reference/{externalReference}` | Resolve a posting outcome after a timeout. |
| Payments | `POST /internal/v1/journals/{journalNumber}/reversals` | Create an immutable opposite journal when downstream settlement fails. |

Despite the existing endpoint name `settlements`, Payments calls it while the payment is in `PENDING_ACCOUNTING`. Payments may assign `SETTLED` only after Accounting posts the journal and all required Deposit/Credit Card projections succeed.

#### Merchant-payment integration gap - high priority

The Payments service also requires:

```text
paymentType = CREDIT_CARD_MERCHANT_PAYMENT
```

Expected logical posting:

```text
Debit  CREDIT_CARD_RECEIVABLE / source card account
Credit MERCHANT_PAYABLE or SETTLEMENT_PAYABLE / merchant
```

Before this payment type is enabled, Accounting and Payments must agree on:

- The exact credit GL account: `MERCHANT_PAYABLE` or `SETTLEMENT_PAYABLE`.
- A stable required `merchantId` and its maximum length/format.
- The merchant subledger mapping code.
- Accounting rule code and initial version.
- Whether merchant settlement is immediate or Accounting only creates a payable awaiting later settlement.

The posting endpoint and request shape can already support this type, but the integration is not complete until those accounting decisions, mappings, and rules are configured. This gap is high priority because it blocks real merchant-payment integration.

#### Refund boundary - medium/future priority

`POST /internal/v1/payment-postings/refunds` creates only the opposite Accounting effect derived from the original journal. A successful refund journal does not automatically:

- Restore a Deposit Account balance.
- Restore a Credit Card available limit.
- Change Credit Card outstanding state.
- Mark the Payments refund lifecycle complete.

Payments remains responsible for coordinating the operational refund projections. The endpoint is idempotent, requires `originalJournalNumber`, and derives the opposite GL entries from the original journal rather than accepting caller-selected GL codes. Refunds are medium/future priority because they are outside the current beginner scope.

### Bill Generation integration

| Consumer | Method and API | Purpose |
|---|---|---|
| Bill Generation | `POST /internal/v1/bill-postings` | Post bill-calculated interest, fee, penalty, and tax components. |
| Bill Generation | `GET /internal/v1/ledger-entries?accountReference=&from=&to=&page=&size=` | Read authoritative card activity for a billing period. |

`POST /internal/v1/bill-postings` is exposed by Accounting and called by Bill Generation. Bill Generation stores the returned `journalNumber` against its bill. Accounting does not fetch the bill or the calculation rules from Billing.

Purchases, repayments, refunds, and reversals already posted through Payments must not be submitted again as bill components.

### Balance and ledger inquiry APIs

| Consumer | Method and API | Purpose |
|---|---|---|
| Deposit Account / authorized peer | `GET /internal/v1/account-balances/{accountReference}` | Return the journal-derived balance for an opaque account reference. |
| Bill Generation | `GET /internal/v1/ledger-entries` | Return paginated journal-line activity for an account and date range. |

### Fixed Deposit posting APIs

Deposit Account owns FD operational state, interest calculations, maturity calculations, premature-closure calculations, and the associated money-movement orchestration. Accounting owns the authoritative journals produced from those calculated financial facts.

The preferred compact API is:

| Consumer | Method and API | Purpose |
|---|---|---|
| Deposit Account | `POST /internal/v1/fixed-deposit-postings` | Post a typed FD financial fact. |
| Deposit Account | `GET /internal/v1/fixed-deposit-postings/by-reference/{postingReference}` | Resolve an unknown result after timeout. |
| Deposit Account | `POST /internal/v1/journals/{journalNumber}/reversals` | Reverse an incorrect FD journal using the shared reversal operation. |

`POST /internal/v1/fixed-deposit-postings` supports:

- `FUNDING`
- `INTEREST_ACCRUAL`
- `INTEREST_PAYOUT`
- `MATURITY_PAYOUT`
- `PREMATURE_CLOSURE`

If the Deposit team requires the explicit URLs from its contract, Accounting may expose these as thin aliases to the same posting engine:

```http
POST /internal/v1/fixed-deposit-postings/fundings
POST /internal/v1/fixed-deposit-postings/interest-accruals
POST /internal/v1/fixed-deposit-postings/interest-payouts
POST /internal/v1/fixed-deposit-postings/maturity-payouts
POST /internal/v1/fixed-deposit-postings/premature-closures
```

Only one canonical financial posting is persisted for a `postingReference`, regardless of which accepted path is used. Deposit must store the returned `journalNumber` against its FD operation.

FD funding, maturity payout, and premature closure are financial postings. They remain separate from the account lifecycle APIs below.

### Deposit and Credit Card account lifecycle APIs

Deposit Account and Credit Card remain the authoritative owners of their operational account status. They notify Accounting so it can register the corresponding subledger reference, reject postings to closed accounts, and provide an Accounting-only clearance result before closure.

| Consumer | Method and API | Purpose |
|---|---|---|
| Deposit Account / Credit Card | `POST /internal/v1/account-lifecycle-events` | Register an account opening or final account closing event. |
| Deposit Account / Credit Card | `GET /internal/v1/account-clearances/{accountType}/{accountReference}?currencyCode=` | Check whether Accounting has any balance or Accounting-owned blocker for the account. |

Supported lifecycle event types are:

- `DEPOSIT_ACCOUNT_OPENED`
- `DEPOSIT_ACCOUNT_CLOSED`
- `CREDIT_CARD_ACCOUNT_OPENED`
- `CREDIT_CARD_ACCOUNT_CLOSED`

`POST /internal/v1/account-lifecycle-events` requires:

- `eventReference`: a stable identifier unique to the lifecycle event.
- `eventType`: one of the four supported values.
- `accountType`: `DEPOSIT_ACCOUNT` or `CREDIT_CARD_ACCOUNT`, consistent with `eventType`.
- `accountReference`: the opaque account identifier owned by the caller.
- `productCode` and `currencyCode` for an opening event.
- `businessDate` and `occurredAt`.
- Optional `reasonCode` for a closing event.
- The standard idempotency, correlation, and authorization headers.

An opening event does not create a zero-value journal. It creates the Accounting subledger registration in `OPEN` state. The owning service activates the operational account only after receiving a successful response.

The clearance response is an Accounting opinion, not permission to close the operational account. It contains:

- `accountType` and `accountReference`.
- `accountingCleared`: `true` or `false`.
- Per-currency, per-logical-role journal-derived balances.
- Accounting-owned blockers such as `NON_ZERO_BALANCE`, `PENDING_POSTING`, or `ACCOUNT_NOT_REGISTERED`.
- `lastPostingSequence` and `checkedAt`.

For a Deposit account, every relevant customer-deposit liability balance must be zero. For a Credit Card account, every relevant receivable, unapplied-credit, billed-charge, fee, interest, tax, and other configured clearance role must be zero. A net total of zero is insufficient if offsetting non-zero role balances remain.

Before checking clearance, the owning service must put the account into `CLOSURE_PENDING`, prevent new operations, drain in-flight requests, and verify its own domain conditions. For example, Deposit must check holds and reservations; Credit Card must check pending authorizations, unbilled transactions, instalments, disputes, and final billing.

When a `*_CLOSED` event is submitted, Accounting re-evaluates clearance and changes its subledger state to `CLOSED` in the same database transaction. If the account is no longer clear, Accounting returns `409 ACCOUNT_NOT_CLEARED` and does not store the closing event. This final atomic check protects against a posting arriving between the earlier clearance query and the closing request.

Once Accounting accepts the closing event, ordinary financial postings referencing that closed account return `409 POSTING_TO_CLOSED_ACCOUNT`. The lifecycle event itself never moves money and never creates a journal.

Lifecycle-specific errors include:

- `404 ACCOUNT_NOT_REGISTERED`
- `409 INVALID_LIFECYCLE_TRANSITION`
- `409 ACCOUNT_NOT_CLEARED`
- `409 POSTING_TO_CLOSED_ACCOUNT`
- `409 IDEMPOTENCY_KEY_REUSED`

The first accepted lifecycle event returns `201 Created`; an identical idempotent replay returns `200 OK` with `idempotentReplay=true`. The clearance query returns `200 OK` even when `accountingCleared=false`, because a non-clear account is a valid business result rather than a transport failure.

After lifecycle integration is enabled, ordinary Deposit and Credit Card postings require a registered `OPEN` subledger. Existing test data must therefore be registered through opening events or a controlled Liquibase bootstrap before strict unknown-account validation is enabled.

### Journal inquiry APIs

| Consumer | Method and API | Purpose |
|---|---|---|
| Frontend / Audit / Operations | `GET /api/v1/journals/{journalNumber}` | Get a journal and all its lines. |
| Frontend / Audit / Operations | `GET /api/v1/journals?businessDate=&sourceService=&eventType=&externalReference=&page=&size=` | Search journals using optional filters. |

### Accounting configuration APIs

| Consumer | Method and API | Purpose |
|---|---|---|
| Accounting Admin | `POST /api/v1/gl-accounts` | Create a GL account. |
| Accounting Admin | `GET /api/v1/gl-accounts` | List active and inactive GL accounts. |
| Accounting Admin | `GET /api/v1/gl-accounts/{glCode}` | Get one GL account. |
| Accounting Admin | `PATCH /api/v1/gl-accounts/{glCode}/status` | Activate or deactivate a GL account without changing history. |
| Accounting Admin | `POST /api/v1/accounting-rules` | Create an effective-dated rule version. |
| Accounting Admin | `GET /api/v1/accounting-rules` | List accounting-rule versions. |
| Accounting Admin | `POST /api/v1/subledger-mappings` | Create an effective-dated mapping from a logical role to a GL account. |
| Accounting Admin | `GET /api/v1/subledger-mappings` | List mappings. |

For the simplest first release, the initial GL accounts, mappings, and rules may be seeded through Liquibase. The query APIs remain useful for explanation and audit; administrative mutation APIs may be delivered after core posting is stable.

### Trial-balance, reconciliation, and period APIs

| Consumer | Method and API | Purpose |
|---|---|---|
| EOD | `POST /internal/v1/trial-balances` | Generate and persist a trial balance by business date and currency. |
| Frontend / Audit / EOD | `GET /api/v1/trial-balances/{runId}` | Retrieve the persisted trial balance. |
| EOD | `POST /internal/v1/eod/reconciliation/runs` | Compare Payments control totals with Accounting journals. |
| Frontend / Audit / EOD | `GET /api/v1/reconciliation/runs/{runId}` | Retrieve expected/actual totals and exceptions. |
| Accounting Operations | `PATCH /api/v1/reconciliation/runs/{runId}/items/{itemId}/resolution` | Resolve or explicitly accept a difference. |
| EOD | `POST /internal/v1/accounting-periods/{businessDate}/open` | Open an Accounting business date. |
| EOD | `POST /internal/v1/accounting-periods/{businessDate}/close` | Close a date after all blocking controls pass. |
| Frontend / Peer | `GET /api/v1/accounting-periods/{businessDate}` | Return `OPEN`, `CLOSING`, or `CLOSED`. |

EOD mutable requests also contain `eodRunId`, `stepCode`, and a stable `commandReference`. Accounting returns the same `commandReference` so EOD can safely recover or retry.

## Data required from other microservices

### From Payments

For a payment posting:

- `paymentId`
- `paymentType`: `BOOK_TRANSFER`, `CREDIT_CARD_REPAYMENT`, or future `CREDIT_CARD_MERCHANT_PAYMENT`
- `source.instrumentType`
- `source.accountId`
- `destination.instrumentType`
- `destination.accountId` or `merchantId`
- `amount`
- `currencyCode`
- `occurredAt`
- `businessDate`
- Optional customer-facing `reference`
- Correlation ID and idempotency key in headers

For a refund:

- `refundId`
- `paymentId`
- `originalJournalNumber`
- `amount`
- `currencyCode`
- `occurredAt`
- `businessDate`
- `reason`

For a reversal:

- Original `journalNumber` in the URL
- `paymentId`
- `businessDate`
- `occurredAt`
- `reason`
- A new stable idempotency key

Accounting does not require customer names, CIF/KYC details, account balances, card numbers, card limits, or payment status.

### From Bill Generation

Accounting receives only financial components calculated by Bill Generation:

- `billId`
- `accountId`
- Optional `productCode`
- Billing-period start and end
- `businessDate`
- `occurredAt`
- `currencyCode`
- Typed components such as `INTEREST`, `LATE_FEE`, `ANNUAL_FEE`, `PENALTY`, and `TAX`

Accounting does not require the bill PDF, customer contact information, minimum amount due, due date, or Billing calculation formulas.

### From Deposit Account for Fixed Deposits

Deposit Account calculates all FD amounts and sends:

- `postingReference`, unique and stable for the financial event
- `postingType`
- `fixedDepositAccountId`
- `productCode`
- `currencyCode`
- `businessDate`
- `occurredAt`
- Typed `components` with their calculated amounts
- `fundingAccountId` or `payoutAccountId` when applicable
- `payoutMode`: `TO_DEPOSIT_ACCOUNT` or `CAPITALIZE`, for interest payout
- Optional `reasonCode` and narration
- Correlation ID and idempotency key in headers

For premature closure, components must identify principal, eligible interest, interest adjustment/reversal, penalty, tax if applicable, and net payout. Accounting validates that the components resolve to a balanced posting but does not calculate them.

Accounting does not require CIF/KYC data, nominee details, customer names, the FD interest formula, or the complete maturity schedule.

### From Deposit Account and Credit Card for account lifecycle

For an opening notification, Accounting receives the lifecycle `eventReference`, event and account types, opaque account reference, product code, currency, business date, and occurrence timestamp. It does not require the customer name, card number, CIF/KYC record, credit limit, available balance, or product pricing details.

For a closing notification, Accounting receives the same account identity and event metadata plus an optional reason code. Deposit Account or Credit Card must resolve all operational blockers before sending the closing event. Accounting independently performs its final ledger-clearance check and does not trust a caller-supplied balance or `cleared=true` flag.

### From EOD/Reconciliation

- `eodRunId`
- `stepCode`
- `commandReference`
- `businessDate`
- `reconciledService`, initially `PAYMENTS-SERVICE`
- `currencyCode`
- `expectedJournalCount`
- `expectedTotalDebit`
- Correlation ID and idempotency key

## Data returned to other microservices

### To Payments, Bill Generation, and Deposit Account FD operations

Accounting returns:

- `journalNumber`
- `postingSequence`
- `externalReference`
- `sourceService`
- `eventType`
- `occurredAt`
- `businessDate`
- `currencyCode`
- `status`
- `totalDebit`
- `totalCredit`
- Applied rule codes and versions
- `correlationId`
- `postedAt`
- `idempotentReplay`
- Typed debit and credit journal lines
- Original/reversed journal reference when applicable

### To Deposit Account and Bill Generation readers

Accounting returns journal-derived balances or paginated ledger entries. These are financial-book values; the account services continue to own available balance, blocked amount, limits, and operational status.

### To Deposit Account and Credit Card lifecycle callers

For an accepted opening or closing event, Accounting returns the event reference, account identity, Accounting lifecycle state, processing timestamp, correlation ID, and `idempotentReplay`. A closing response is successful only after the final atomic clearance check passes.

For a clearance query, Accounting returns `accountingCleared`, the balances inspected, any Accounting-owned blockers, the last posting sequence, and the check timestamp. The caller combines that response with its own operational checks before deciding whether the customer account can close.

### To EOD

Accounting returns:

- Trial-balance totals and `balanced` status
- Reconciliation expected/actual totals
- Blocking exception items
- Accounting-period state
- Stable command reference and completion timestamp

## Accounting rules required for the current flows

| Financial event/component | Debit | Credit |
|---|---|---|
| Book transfer | `CUSTOMER_DEPOSIT_LIABILITY`, source account | `CUSTOMER_DEPOSIT_LIABILITY`, destination account |
| Credit-card repayment | `CUSTOMER_DEPOSIT_LIABILITY`, source deposit | `CREDIT_CARD_RECEIVABLE`, card account |
| Credit-card merchant payment | `CREDIT_CARD_RECEIVABLE`, source card account | Pending decision: `MERCHANT_PAYABLE` or `SETTLEMENT_PAYABLE`, merchant subledger |
| Bill interest | `CREDIT_CARD_RECEIVABLE`, card account | `INTEREST_INCOME` |
| Bill fee or penalty | `CREDIT_CARD_RECEIVABLE`, card account | `FEE_INCOME` |
| Bill tax | `CREDIT_CARD_RECEIVABLE`, card account | `TAX_PAYABLE` |
| FD funding from deposit | `CUSTOMER_DEPOSIT_LIABILITY`, funding account | `FIXED_DEPOSIT_LIABILITY`, FD account |
| FD interest accrual | `DEPOSIT_INTEREST_EXPENSE` | `FD_INTEREST_PAYABLE`, FD account |
| FD interest payout to deposit | `FD_INTEREST_PAYABLE`, FD account | `CUSTOMER_DEPOSIT_LIABILITY`, payout account |
| FD interest capitalization | `FD_INTEREST_PAYABLE`, FD account | `FIXED_DEPOSIT_LIABILITY`, FD account |
| FD maturity payout | `FIXED_DEPOSIT_LIABILITY` plus `FD_INTEREST_PAYABLE`, FD account | `CUSTOMER_DEPOSIT_LIABILITY`, payout account |
| FD premature closure | Component-based rules for principal, adjusted interest, penalty, and tax | Net amount to `CUSTOMER_DEPOSIT_LIABILITY`, payout account |
| Reversal | Opposite of each original journal line | Opposite of each original journal line |

The merchant-payment row is a required unresolved configuration decision. It must not be activated until the payable/settlement semantics and merchant mapping are approved.

## Oracle schema and tables

Accounting uses its own Oracle user/schema, `MONEYBAGS_ACCOUNTING`. Liquibase is the only schema-migration mechanism, and Hibernate uses `ddl-auto=validate`.

| Table | Purpose |
|---|---|
| `GL_ACCOUNT` | Accounting-owned chart of accounts. |
| `ACCOUNTING_RULE` | Effective-dated debit/credit rule versions. |
| `SUBLEDGER_MAPPING` | Effective-dated logical-role-to-GL mappings. |
| `SUBLEDGER_ACCOUNT` | Accounting projection of an external Deposit or Credit Card account and its `OPEN`/`CLOSED` state. |
| `ACCOUNT_LIFECYCLE_EVENT` | Immutable, idempotent opening and closing notifications received from account-owning services. |
| `POSTING_REQUEST` | Permanent financial source-reference idempotency and posting outcome. |
| `JOURNAL` | Immutable balanced journal header. |
| `JOURNAL_LINE` | Immutable debit and credit lines. |
| `ACCOUNTING_PERIOD` | Open/closing/closed business-date control. |
| `AUDIT_LOG` | Immutable administrative and operational audit evidence. |
| `IDEMPOTENCY_RECORD` | Replay protection for general admin and EOD commands. |
| `TRIAL_BALANCE_RUN` | Persisted trial-balance header and totals. |
| `TRIAL_BALANCE_LINE` | Per-GL trial-balance totals and closing balance. |
| `FIN_RECON_RUN` | Payments-to-Accounting reconciliation totals. |
| `FIN_RECON_ITEM` | Individual reconciliation differences and resolutions. |

`SUBLEDGER_ACCOUNT` has a unique key on `(ACCOUNT_TYPE, ACCOUNT_REFERENCE)` and stores product code, currency, lifecycle state, opened/closed timestamps, source service, and an optimistic-lock version. `ACCOUNT_LIFECYCLE_EVENT` has unique constraints on `EVENT_REFERENCE` and the idempotency key/request hash. Valid state transitions in the current scope are `UNREGISTERED -> OPEN -> CLOSED`; reopening is not supported.

Opening and closing lifecycle events do not create rows in `JOURNAL` or `JOURNAL_LINE`. Clearance is calculated from journals, posting-request state, and the current subledger state; a separate mutable balance supplied by Deposit or Credit Card is not stored as Accounting truth.

FD postings reuse `POSTING_REQUEST`, `JOURNAL`, and `JOURNAL_LINE`; no separate FD Accounting table is required. Trial-balance and financial-reconciliation tables can be delivered with EOD integration.

The current synchronous project does not use Kafka or RabbitMQ. An `ACCOUNTING_OUTBOX_EVENT` table and event relay must not be described as operational until the team introduces a real broker and publisher. `JournalPosted`, `JournalRejected`, and `ReconciliationExceptionRaised` remain possible future asynchronous events.

## Example 1 - Book transfer posting

Payments calls Accounting after reserving the source funds and moving the payment to `PENDING_ACCOUNTING`.

```http
POST /internal/v1/payment-postings/settlements
Idempotency-Key: PAYMENT:PAY-1001:ACCOUNTING
X-Correlation-Id: trace-pay-1001
```

```json
{
  "paymentId": "PAY-1001",
  "paymentType": "BOOK_TRANSFER",
  "source": {
    "instrumentType": "DEPOSIT_ACCOUNT",
    "accountId": "DEP-1001"
  },
  "destination": {
    "instrumentType": "DEPOSIT_ACCOUNT",
    "accountId": "DEP-2001"
  },
  "amount": 5000.00,
  "currencyCode": "INR",
  "occurredAt": "2026-08-13T10:30:00Z",
  "businessDate": "2026-08-13",
  "reference": "Rent payment"
}
```

Accounting resolves the book-transfer rule and posts:

```text
Debit  CUSTOMER_DEPOSIT_LIABILITY / DEP-1001  INR 5,000
Credit CUSTOMER_DEPOSIT_LIABILITY / DEP-2001  INR 5,000
```

Response:

```json
{
  "journalNumber": "JRN-20260813-000001",
  "postingSequence": 1,
  "externalReference": "PAYMENT:PAY-1001:ACCOUNTING",
  "sourceService": "PAYMENTS-SERVICE",
  "eventType": "BOOK_TRANSFER",
  "occurredAt": "2026-08-13T10:30:00Z",
  "businessDate": "2026-08-13",
  "currencyCode": "INR",
  "status": "POSTED",
  "totalDebit": 5000.00,
  "totalCredit": 5000.00,
  "correlationId": "trace-pay-1001",
  "postedAt": "2026-08-13T10:30:01Z",
  "idempotentReplay": false,
  "lines": [
    {
      "lineNumber": 1,
      "glCode": "CUSTOMER_DEPOSIT_LIABILITY",
      "subledgerReference": "DEP-1001",
      "componentType": "PRINCIPAL",
      "ruleCode": "BOOK_TRANSFER_PRINCIPAL",
      "ruleVersion": 1,
      "debitAmount": 5000.00,
      "creditAmount": 0
    },
    {
      "lineNumber": 2,
      "glCode": "CUSTOMER_DEPOSIT_LIABILITY",
      "subledgerReference": "DEP-2001",
      "componentType": "PRINCIPAL",
      "ruleCode": "BOOK_TRANSFER_PRINCIPAL",
      "ruleVersion": 1,
      "debitAmount": 0,
      "creditAmount": 5000.00
    }
  ]
}
```

Payments stores `journalNumber`, asks Deposit Account to settle the reserved transfer, and only then marks the payment `SETTLED`.

## Example 2 - Credit-card repayment

```json
{
  "paymentId": "PAY-2001",
  "paymentType": "CREDIT_CARD_REPAYMENT",
  "source": {
    "instrumentType": "DEPOSIT_ACCOUNT",
    "accountId": "DEP-1001"
  },
  "destination": {
    "instrumentType": "CREDIT_CARD_ACCOUNT",
    "accountId": "CC-5001"
  },
  "amount": 12500.00,
  "currencyCode": "INR",
  "occurredAt": "2026-08-13T11:00:00Z",
  "businessDate": "2026-08-13",
  "reference": "Credit-card bill repayment"
}
```

Accounting posts:

```text
Debit  CUSTOMER_DEPOSIT_LIABILITY / DEP-1001  INR 12,500
Credit CREDIT_CARD_RECEIVABLE / CC-5001        INR 12,500
```

Payments then captures the Deposit reservation, applies the repayment through Credit Card, and marks the payment `SETTLED`. If a downstream step fails after journal posting, Payments calls the reversal API and moves through `REVERSAL_PENDING` to `REVERSED`.

## Example 3 - Bill-generated charges

Bill Generation calculates charges and calls the endpoint exposed by Accounting:

```http
POST /internal/v1/bill-postings
Idempotency-Key: BILL:BILL-3001:ACCOUNTING
X-Correlation-Id: trace-bill-3001
```

```json
{
  "billId": "BILL-3001",
  "accountId": "CC-5001",
  "productCode": "CARD-GOLD",
  "billingPeriodStart": "2026-07-01",
  "billingPeriodEnd": "2026-07-31",
  "businessDate": "2026-08-01",
  "occurredAt": "2026-08-01T01:00:00Z",
  "currencyCode": "INR",
  "components": [
    {
      "componentType": "INTEREST",
      "amount": 500.00
    },
    {
      "componentType": "LATE_FEE",
      "amount": 100.00
    },
    {
      "componentType": "TAX",
      "amount": 108.00
    }
  ]
}
```

Accounting posts:

```text
Debit  CREDIT_CARD_RECEIVABLE / CC-5001  INR 708
Credit INTEREST_INCOME                    INR 500
Credit FEE_INCOME                         INR 100
Credit TAX_PAYABLE                        INR 108
```

Accounting returns a balanced `JournalResponse`; Bill Generation stores its `journalNumber` against the bill.

## Example 4 - Reversal after downstream failure

```http
POST /internal/v1/journals/JRN-20260813-000001/reversals
Idempotency-Key: PAYMENT:PAY-1001:REVERSAL
X-Correlation-Id: trace-pay-1001
```

```json
{
  "paymentId": "PAY-1001",
  "businessDate": "2026-08-13",
  "occurredAt": "2026-08-13T10:35:00Z",
  "reason": "Deposit settlement failed after Accounting posted the journal"
}
```

Accounting creates a new journal containing the opposite of every original line and returns both the new reversal journal number and the original journal number. The original journal remains unchanged.

## Example 5 - Fixed Deposit interest accrual

Deposit Account calculates the accrual and submits the financial fact:

```http
POST /internal/v1/fixed-deposit-postings
Idempotency-Key: FD:FD-1001:ACCRUAL:2026-08-31
X-Correlation-Id: trace-fd-1001-august
```

```json
{
  "postingReference": "FD:FD-1001:ACCRUAL:2026-08-31",
  "postingType": "INTEREST_ACCRUAL",
  "fixedDepositAccountId": "FD-1001",
  "productCode": "FD-12M-STANDARD",
  "currencyCode": "INR",
  "businessDate": "2026-08-31",
  "occurredAt": "2026-08-31T18:00:00Z",
  "components": [
    {
      "componentType": "INTEREST",
      "amount": 650.00
    }
  ],
  "narration": "August FD interest accrual"
}
```

Accounting posts:

```text
Debit  DEPOSIT_INTEREST_EXPENSE        INR 650
Credit FD_INTEREST_PAYABLE / FD-1001   INR 650
```

Response excerpt:

```json
{
  "journalNumber": "JRN-20260831-000101",
  "externalReference": "FD:FD-1001:ACCRUAL:2026-08-31",
  "sourceService": "DEPOSIT-ACCOUNT-SERVICE",
  "eventType": "FD_INTEREST_ACCRUAL",
  "businessDate": "2026-08-31",
  "currencyCode": "INR",
  "status": "POSTED",
  "totalDebit": 650.00,
  "totalCredit": 650.00,
  "idempotentReplay": false
}
```

Deposit stores the returned `journalNumber` against its accrual. If the response is lost, Deposit queries the same `postingReference` before retrying.

## Example 6 - Credit-card merchant payment request

```json
{
  "paymentId": "PAY-4001",
  "paymentType": "CREDIT_CARD_MERCHANT_PAYMENT",
  "source": {
    "instrumentType": "CREDIT_CARD_ACCOUNT",
    "accountId": "CC-5001"
  },
  "destination": {
    "instrumentType": "MERCHANT",
    "merchantId": "MERCHANT-9001"
  },
  "amount": 2000.00,
  "currencyCode": "INR",
  "occurredAt": "2026-08-13T16:00:00Z",
  "businessDate": "2026-08-13",
  "reference": "Merchant purchase"
}
```

Accounting will post:

```text
Debit  CREDIT_CARD_RECEIVABLE / CC-5001       INR 2,000
Credit <APPROVED_MERCHANT_PAYABLE_GL> / MERCHANT-9001  INR 2,000
```

This request must remain disabled until the team approves the exact merchant payable GL, mapping code, rule version, required merchant identifier, and settlement timing.

## Example 7 - Deposit account opening

Deposit Account first creates its account in a non-active state and then registers the lifecycle fact with Accounting:

```http
POST /internal/v1/account-lifecycle-events
Idempotency-Key: DEPOSIT:DEP-3001:OPEN
X-Correlation-Id: trace-dep-3001-open
```

```json
{
  "eventReference": "DEPOSIT:DEP-3001:OPEN",
  "eventType": "DEPOSIT_ACCOUNT_OPENED",
  "accountType": "DEPOSIT_ACCOUNT",
  "accountReference": "DEP-3001",
  "productCode": "SAVINGS-STANDARD",
  "currencyCode": "INR",
  "businessDate": "2026-08-14",
  "occurredAt": "2026-08-14T09:00:00Z"
}
```

Accounting registers the subledger but creates no journal:

```json
{
  "eventReference": "DEPOSIT:DEP-3001:OPEN",
  "accountType": "DEPOSIT_ACCOUNT",
  "accountReference": "DEP-3001",
  "accountingLifecycleState": "OPEN",
  "processedAt": "2026-08-14T09:00:01Z",
  "correlationId": "trace-dep-3001-open",
  "idempotentReplay": false
}
```

Deposit Account may now change its operational status to `ACTIVE`. If the response is lost, it retries the identical request with the same idempotency key.

## Example 8 - Credit Card closure blocked and later completed

Credit Card first changes its operational account to `CLOSURE_PENDING`, blocks new transactions, and checks its own pending authorizations, unbilled activity, instalments, disputes, and final bill. It then asks Accounting:

```http
GET /internal/v1/account-clearances/CREDIT_CARD_ACCOUNT/CC-5001?currencyCode=INR
X-Correlation-Id: trace-cc-5001-close
```

If the card still has a receivable, Accounting responds:

```json
{
  "accountType": "CREDIT_CARD_ACCOUNT",
  "accountReference": "CC-5001",
  "accountingCleared": false,
  "balances": [
    {
      "currencyCode": "INR",
      "logicalRole": "CREDIT_CARD_RECEIVABLE",
      "amount": 3250.00
    }
  ],
  "blockers": ["NON_ZERO_BALANCE"],
  "lastPostingSequence": 1842,
  "checkedAt": "2026-08-14T12:00:00Z"
}
```

Credit Card must not close the account. It tells the customer that the outstanding amount must be settled and keeps the account in `CLOSURE_PENDING` or returns it to the appropriate operational state. Accounting does not initiate payment and does not waive the amount.

After Payments records the repayment, Credit Card applies its operational projection, Bill Generation posts any final charges, and every configured Accounting clearance role is zero, the same GET returns `accountingCleared=true`. Credit Card then sends the final lifecycle event:

```http
POST /internal/v1/account-lifecycle-events
Idempotency-Key: CREDIT-CARD:CC-5001:CLOSE
X-Correlation-Id: trace-cc-5001-close
```

```json
{
  "eventReference": "CREDIT-CARD:CC-5001:CLOSE",
  "eventType": "CREDIT_CARD_ACCOUNT_CLOSED",
  "accountType": "CREDIT_CARD_ACCOUNT",
  "accountReference": "CC-5001",
  "currencyCode": "INR",
  "businessDate": "2026-08-14",
  "occurredAt": "2026-08-14T14:30:00Z",
  "reasonCode": "CUSTOMER_REQUEST"
}
```

Accounting checks clearance again inside the closing transaction and, if it remains clear, returns:

```json
{
  "eventReference": "CREDIT-CARD:CC-5001:CLOSE",
  "accountType": "CREDIT_CARD_ACCOUNT",
  "accountReference": "CC-5001",
  "accountingLifecycleState": "CLOSED",
  "processedAt": "2026-08-14T14:30:01Z",
  "correlationId": "trace-cc-5001-close",
  "idempotentReplay": false
}
```

Only after that response does Credit Card mark its operational account `CLOSED`. Deposit Account follows the same pattern, using the Deposit event types and checking that its own holds, reservations, and pending operations are clear.

## Flow completion rules

### Book transfer

```text
Payments validates/reserves Deposit funds
-> Payments enters PENDING_ACCOUNTING
-> Accounting posts journal
-> Payments settles Deposit projection
-> Payments enters SETTLED
```

### Credit-card repayment

```text
Payments validates Card and reserves Deposit funds
-> Payments enters PENDING_ACCOUNTING
-> Accounting posts journal
-> Payments captures Deposit reservation
-> Payments applies Credit Card repayment projection
-> Payments enters SETTLED
```

If any required projection fails after Accounting posts, Payments—not Accounting—coordinates compensation and requests the journal reversal.

### Fixed Deposit financial lifecycle

```text
Deposit Account calculates and executes the FD operational step
-> Deposit submits the typed FD financial fact with a stable postingReference
-> Accounting creates the balanced journal and returns journalNumber
-> Deposit stores the journalNumber against its FD operation
-> On timeout, Deposit looks up the posting before retrying
```

Deposit Account, not Payments, owns the orchestration of FD funding, interest payout, maturity payout, and premature-closure payout in the current project scope. This ownership prevents Payments and Deposit from posting the same FD movement twice.

### Deposit or Credit Card account opening

```text
Account service creates operational account as PENDING_ACTIVATION
-> Account service posts the *_OPENED lifecycle event
-> Accounting registers the OPEN subledger without a journal
-> Account service marks the operational account ACTIVE
```

If Accounting rejects or times out, the account service must not activate the account. It retries the identical idempotent request to resolve an uncertain result.

### Deposit or Credit Card account closure

```text
Account service receives the customer's closure request
-> Account service enters CLOSURE_PENDING and blocks new activity
-> Account service drains in-flight work and checks its domain-specific blockers
-> Account service GETs Accounting clearance
-> If not clear, the account stays open/pending until the balance or blocker is resolved
-> If clear, the account service POSTs the *_CLOSED lifecycle event
-> Accounting atomically rechecks clearance and marks its subledger CLOSED
-> Account service marks the operational account CLOSED
```

Accounting's `accountingCleared=true` covers only Accounting-owned books. It does not replace Deposit checks for holds/reservations or Credit Card checks for pending authorizations, unbilled activity, instalments, disputes, and final billing.

### End-of-day closure

```text
EOD freezes cutoff and drains Payments
-> EOD completes or classifies Billing
-> Accounting generates trial balance
-> Accounting reconciles Payments totals
-> Blocking differences are resolved
-> Accounting closes the period
-> EOD opens the next business date
```

## Implementation sequence

### Release 1 - Core ledger

1. Create the `accounting` Maven module on port 8088.
2. Configure Oracle `MONEYBAGS_ACCOUNTING`, Liquibase, and `ddl-auto=validate`.
3. Create and seed the core chart, rules, and mappings.
4. Implement payment posting, FD posting, bill posting, outcome lookup, and reversal.
5. Implement account lifecycle registration, Accounting clearance, and closed-account posting protection.
6. Implement journal, balance, and ledger-entry inquiries.
7. Add gateway routes for `/api/v1/**` only.
8. Add security, validation, correlation IDs, problem details, Actuator, and metrics.
9. Add unit, integration, idempotency, lifecycle-race, concurrency, and Liquibase tests.

### Release 2 - EOD controls

1. Implement trial-balance persistence and APIs.
2. Implement Payments reconciliation and exception resolution.
3. Implement Accounting period open/close controls.
4. Add EOD consumer/provider contract and end-to-end tests.

## Integration acceptance checklist

- Payments sends `paymentType`, typed source/destination references, amount, currency, occurrence time, and business date.
- Payments stores the returned `journalNumber` and does not mark `SETTLED` before all projections succeed.
- `CREDIT_CARD_MERCHANT_PAYMENT` remains disabled until its merchant payable GL, mapping, merchant identifier, rule version, and settlement timing are approved.
- A successful Accounting refund reverses only the Accounting effect; Payments coordinates Deposit/Card operational restoration.
- Bill Generation calls Accounting's `/internal/v1/bill-postings` endpoint and never reposts Payments activity.
- Deposit Account owns FD orchestration and submits typed FD financial facts with unique posting references.
- Deposit Account and Credit Card register opening and closing events through the shared lifecycle endpoint.
- An opening event registers the Accounting subledger but does not create a financial journal.
- Before closure, the owning service blocks activity and clears its own operational conditions; Accounting reports only its ledger clearance.
- Accounting atomically rechecks clearance when accepting a closing event and rejects ordinary postings to a closed subledger.
- Deposit Account never sends calculated GL codes; Accounting resolves all FD GL mappings and rules.
- Every caller reuses the same idempotency key and payload after a timeout.
- Internal APIs are absent from public gateway routes.
- Accounting has a dedicated Oracle schema and Liquibase changelog.
- Posted journals cannot be updated or deleted.
- Every journal and reversal satisfies total debit equals total credit.
- EOD cannot close a period with an unbalanced trial balance or unresolved blocking reconciliation item.
