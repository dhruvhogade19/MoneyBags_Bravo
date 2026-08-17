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

An EOD run performs Payments cutoff/drain, credit-card and deposit readiness, CASA and fixed-deposit accruals, fixed-deposit maturities, bill close, trial balance, payment and fixed-deposit reconciliation, statement generation, notification delivery, current-period close, and next-period open. Each mutation receives a stable `Idempotency-Key` and all calls carry the EOD run ID as `X-Correlation-Id`.

Configure peer URLs with `PAYMENTS_URL`, `CREDIT_CARD_URL`, `DEPOSIT_ACCOUNT_URL`, `BILL_GENERATION_URL`, `ACCOUNTING_URL`, `STATEMENTS_URL`, and `NOTIFICATION_URL`. The repository currently has no Statements module, so a service implementing `POST /internal/v1/statements/eod/generate` must be available on `STATEMENTS_URL` (default port `8089`) for a run to complete.

For secured local runs, `M2M_CLIENT_SECRET` must match Identity Access Service. Other useful settings are `EOD_INITIAL_BUSINESS_DATE`, `EOD_CURRENCY`, and `EOD_NOTIFICATION_CIF_ID`.

The database connection is read from the root `.env` using `MONEYBAGS_DB_URL`, `MONEYBAGS_DB_USERNAME`, and `MONEYBAGS_DB_PASSWORD`. Optional EOD-only overrides are `MONEYBAGS_EOD_DB_URL`, `MONEYBAGS_EOD_DB_USERNAME`, and `MONEYBAGS_EOD_DB_PASSWORD`. Hibernate only validates the schema; Liquibase owns every database change.

Import `eod-reconciliation.postman_collection.json` to exercise the direct API.
