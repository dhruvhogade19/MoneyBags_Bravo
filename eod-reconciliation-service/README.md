# EOD Reconciliation Service

This service coordinates the end-of-day business-date close. It is registered in Eureka as `eod-reconciliation-service` and runs on port `8091`.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/business-date` | Read the current business date and state. |
| POST | `/api/v1/eod/runs` | Start an EOD run. Requires an `Idempotency-Key` header. |
| GET | `/api/v1/eod/runs?businessDate=YYYY-MM-DD` | List recent runs, optionally filtered by business date. |
| GET | `/api/v1/eod/runs/{runId}` | Read an EOD run. |
| POST | `/api/v1/eod/runs/{runId}/resume` | Audited, idempotent manual continuation. |
| POST | `/api/v1/eod/runs/{runId}/steps/{stepCode}/retry` | Retry a failed step with a fresh execution epoch. |
| POST | `/api/v1/eod/exceptions/{exceptionId}/resolve` | Resolve or waive an exception without bypassing its control. |
| POST | `/api/v1/business-date/open-next` | Open the next business date after a completed run. |

Swagger is available at `http://localhost:8091/swagger-ui.html`. Through the API gateway, use `http://localhost:8080/api/v1/eod/...` and `http://localhost:8080/api/v1/business-date/...`.

Business-date state, runs, step checkpoints, exceptions, idempotency keys, and operator actions are persisted in Oracle. Liquibase creates the service-owned `EOD_BUSINESS_DATE`, `EOD_RUN`, `EOD_RUN_STEP`, `EOD_EXCEPTION`, and `EOD_RUN_ACTION` tables. The service deliberately does not access another service's tables; all operational data still comes from internal HTTP APIs.

Starting a run returns immediately so the existing operations UI can poll the same response contract and persisted checkpoints. New runs snapshot workflow `EOD-2026.2`: all 15 step contracts, dependencies, authentication modes, retry limits, and contract versions are stored with the run. A deployment can therefore resume an older run using that run's original order and endpoints instead of silently replacing it with the current registry.

The workflow ensures the current Accounting period is open, establishes an owned Payments cutoff/drain fence, checks composite Credit Card and Deposit readiness, posts CASA and fixed-deposit accruals and maturities, closes billing, generates the trial balance, and reconciles authoritative posted journal totals. It then closes the current Accounting period and opens the next. `PAYMENTS_REOPEN` is the single required finalizer. An early readiness failure reopens the same business date. Once financial mutation step 6 has started, a failure is instead marked `HELD_FOR_EOD_RECOVERY` and intake remains fenced until an operator resumes and completes the remaining work; this prevents stale accruals or snapshots. The held resume reasserts cutoff/drain under the same owner and does not duplicate already-completed financial mutations.

On success the local date is durably prepared as non-open before Payments is released on D+1. Only after the finalizer response is checkpointed does the service mark the run complete and open the prepared date. Startup and scheduled recovery reclaim expired leases, finish this checkpoint after crashes, and retry only transient or unknown finalizer outcomes. Permanent ownership/configuration conflicts require an operator action. Persisted workflows with zero or multiple `PAYMENTS_REOPEN` finalizers fail closed as `WORKFLOW_FINALIZER_INVALID`.

Infrastructure failures such as connection errors, HTTP 429, and HTTP 5xx responses use bounded exponential retries. `PAYMENTS_NOT_DRAINED` is a bounded poll behind the still-owned fence (nine attempts, roughly 32 seconds of backoff). Other business control failures remain visible for an audited operator retry. Automatic retries reuse the same epoch idempotency key, while a manual retry uses a fresh epoch so a corrected upstream result is not hidden by an old cached response. The stable EOD run correlation and business command reference do not change, preventing duplicate financial effects. Database leases and worker tokens prevent stale instances from overwriting a successor and permit takeover after a crashed worker's lease expires.

All current peer contracts use dedicated M2M endpoints. Known public paths stored by older runs are adapted to their equivalent internal routes at execution time without altering the persisted path shown by the UI. Resume, retry, and exception-resolution requests accept `Idempotency-Key`; the key, action kind, and request hash are stored in `EOD_RUN_ACTION`, identical network replays are harmless, and changed key reuse returns conflict. Actors, reasons, automatic retries, failures, and completion are also audited there.

Ordinary demo skip lists require the `demo` Spring profile and are limited to non-financial statement/notification work. For a fully local UI demonstration, an additional explicit wildcard is available: start with the `demo` profile, set `EOD_DEMO_MODE=true`, and set `EOD_DEMO_SKIPPED_STEPS=*`. Only that three-part combination completes all 15 persisted steps from synthetic contract-shaped outputs without peer HTTP calls. The outputs include `demoMode=true`, `bypassed=true`, `controlBypassed=true`, and `syntheticSuccess=true`; none is reported as `SKIPPED`. The finalizer reports Payments open on D+1 and the local business date advances normally. The wildcard is ignored outside the `demo` profile. Both environment variables default to disabled/empty and are not enabled by repository configuration.

Configure peer URLs with `PAYMENTS_URL`, `CREDIT_CARD_URL`, `DEPOSIT_ACCOUNT_URL`, `BILL_GENERATION_URL`, and `ACCOUNTING_URL`. Statement generation is intentionally outside the blocking close sequence because the current Statements API is account-scoped rather than an EOD batch contract.

Incoming EOD management APIs require a `BANK_ADMIN` JWT when security is enabled. For secured peer calls, `M2M_CLIENT_SECRET` must match Identity Access Service. The dedicated client requests `account:service`, `accounting:service`, `billing:service`, `payment:service`, `statements:service`, `notification:service`, and `card:eod`. Other useful settings are `EOD_INITIAL_BUSINESS_DATE`, `EOD_CURRENCY`, `EOD_EXECUTION_LEASE_SECONDS`, and `EOD_RETRY_BACKOFF_ENABLED`.

The database connection is read from the root `.env` using `MONEYBAGS_DB_URL`, `MONEYBAGS_DB_USERNAME`, and `MONEYBAGS_DB_PASSWORD`. Optional EOD-only overrides are `MONEYBAGS_EOD_DB_URL`, `MONEYBAGS_EOD_DB_USERNAME`, and `MONEYBAGS_EOD_DB_PASSWORD`. Hibernate only validates the schema; Liquibase owns every database change.

Import `eod-reconciliation.postman_collection.json` to exercise the direct API.
