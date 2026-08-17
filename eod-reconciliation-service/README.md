# EOD / Reconciliation Service

Java 25 and Spring Boot 4.1.0 implementation of the Moneybags end-of-day closure orchestrator on port `8091`.

## Local dummy mode

The default configuration needs no database and no peer microservices:

- `EOD_PERSISTENCE=memory` selects the in-memory repository adapters.
- `EOD_STUB_PEER_CLIENTS=true` selects deterministic dummy peer responses.
- `EOD_STUB_FAIL_ON=DEPOSIT_READINESS` can simulate a failure at any named EOD step.
- `EOD_INITIAL_BUSINESS_DATE=2026-08-13` controls the initially open date.

State is intentionally reset when the service restarts. Every completed step contains `"dummy": true` and useful sample counts or totals.

## Replace dummy dependencies later

Implement these ports without changing the controllers or orchestration service:

- `EodRunRepository`, `BusinessDateRepository`, and `IdempotencyStore`: add Oracle/JPA adapters for the `MONEYBAGS_EOD` schema, then select them with `EOD_PERSISTENCE=oracle`.
- `PeerOperations`: add an HTTP/OpenFeign adapter using the canonical peer paths, then set `EOD_STUB_PEER_CLIENTS=false`.

The ordered workflow is payment cutoff, payment drain, credit-card readiness, deposit readiness, deposit accruals,
FD interest accrual, FD maturity processing, FD accounting reconciliation, FD readiness check, bill close,
trial balance, financial reconciliation, statement generation, notification, and accounting-period close.

CASA accrual commands include the required currency:

```json
{
  "eodRunId": "EOD-20260814-ab12cd34",
  "commandReference": "DEP-ACCRUAL-20260814-V1",
  "businessDate": "2026-08-14",
  "currency": "INR"
}
```

FD peer calls are:

- `POST /internal/v1/deposit-accounts/eod/fixed-deposit-accruals` (`Idempotency-Key` required)
- `POST /internal/v1/deposit-accounts/eod/fixed-deposit-maturities` (`Idempotency-Key` required)
- `POST /internal/v1/accounting/fixed-deposit-reconciliation`
- `GET /internal/v1/deposit-accounts/eod/fixed-deposit-readiness` (no request body)

## Quick test

```powershell
$body = @{ businessDate = "2026-08-13"; startedBy = "eod.operator" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8091/api/v1/eod/runs `
  -Headers @{ "Idempotency-Key" = "eod-2026-08-13" } -ContentType application/json -Body $body
```

Swagger UI: `http://localhost:8091/swagger-ui.html`
