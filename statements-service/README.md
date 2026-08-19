# Statements Service

Statements exposes account-wise recent activity by treating Deposit Account's posted debit/credit transactions as the
authoritative activity and balance source, then matching those transactions to Accounting ledger entries. It stores
immutable statement headers, line snapshots, source transaction/payment references, and a downloadable PDF in its own
Oracle schema.

- `GET /api/v1/statements/accounts/{accountId}/activity?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `POST /api/v1/statements` with `accountReference`, `periodStart`, and `periodEnd`
- `POST /internal/v1/statements/generate` (service-only; not an EOD endpoint)
- `GET /api/v1/statements/{statementId}`
- `GET /api/v1/statements/{statementId}/download`

Consumer access is checked against active Deposit account holders using the JWT `customer_id`; administrators use the
`statements:admin` scope. The period is inclusive and limited to 366 days. Activity can still be previewed when
Accounting is unavailable. By default, PDF generation requires the Deposit activity and balance projection to be
internally consistent but does not require Accounting matches. Set
`STATEMENTS_REQUIRE_ACCOUNTING_RECONCILIATION=true` to restore strict Deposit/Accounting reconciliation.

The service runs on port `8089`, registers as `statements-service`, and is available through the Gateway under
`/api/v1/statements/**`. It obtains client-credentials tokens as `statements-service` before calling Deposit and
Accounting internal APIs. Configure the shared database and identity variables from the root `.env`; optional direct
local overrides are `DEPOSIT_ACCOUNT_URL` and `ACCOUNTING_URL`.

Build and test from the repository root:

```powershell
mvn -pl statements-service -am test
```
