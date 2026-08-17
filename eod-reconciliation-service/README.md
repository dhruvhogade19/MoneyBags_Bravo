# EOD Reconciliation Service

This service coordinates the end-of-day business-date close. It is registered in Eureka as `eod-reconciliation-service` and runs on port `8091`.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/business-date` | Read the current business date and state. |
| POST | `/api/v1/eod/runs` | Start an EOD run. Requires an `Idempotency-Key` header. |
| GET | `/api/v1/eod/runs/{runId}` | Read an EOD run. |
| POST | `/api/v1/business-date/open-next` | Open the next business date after a completed run. |

Swagger is available at `http://localhost:8091/swagger-ui.html`. Through the API gateway, use `http://localhost:8080/api/v1/eod/...` and `http://localhost:8080/api/v1/business-date/...`.

Business-date state, runs, step checkpoints, exceptions, and idempotency keys are persisted in Oracle. Liquibase creates the service-owned `EOD_BUSINESS_DATE`, `EOD_RUN`, `EOD_RUN_STEP`, and `EOD_EXCEPTION` tables. The service deliberately does not access another service's tables; all operational data still comes from the internal HTTP APIs.

An EOD run ensures the current Accounting period is open, performs Payments cutoff/drain, credit-card and deposit readiness, CASA and fixed-deposit accruals, fixed-deposit maturities, bill close, trial balance, and payment/fixed-deposit reconciliation. It then closes the current Accounting period, opens the next period, and reopens Payments intake. Each peer mutation receives a stable `Idempotency-Key`, and all calls carry the EOD run ID as `X-Correlation-Id`.

Configure peer URLs with `PAYMENTS_URL`, `CREDIT_CARD_URL`, `DEPOSIT_ACCOUNT_URL`, `BILL_GENERATION_URL`, and `ACCOUNTING_URL`. The repository currently has no Statements module, so statement generation and its `STATEMENT_READY` notification are disabled by default. After deploying a service implementing `POST /internal/v1/statements/eod/generate`, set `EOD_STATEMENTS_ENABLED=true` and configure `STATEMENTS_URL` and `NOTIFICATION_URL` to include both steps.

Incoming EOD management APIs require a `BANK_ADMIN` JWT when security is enabled. For secured peer calls, `M2M_CLIENT_SECRET` must match Identity Access Service. Other useful settings are `EOD_INITIAL_BUSINESS_DATE`, `EOD_CURRENCY`, and `EOD_NOTIFICATION_CIF_ID`.

The database connection is read from the root `.env` using `MONEYBAGS_DB_URL`, `MONEYBAGS_DB_USERNAME`, and `MONEYBAGS_DB_PASSWORD`. Optional EOD-only overrides are `MONEYBAGS_EOD_DB_URL`, `MONEYBAGS_EOD_DB_USERNAME`, and `MONEYBAGS_EOD_DB_PASSWORD`. Hibernate only validates the schema; Liquibase owns every database change.

Import `eod-reconciliation.postman_collection.json` to exercise the direct API.
