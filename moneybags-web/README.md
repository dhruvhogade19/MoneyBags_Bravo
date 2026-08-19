# MoneyBags Oracle JET frontend

This module is the customer banking and bank-operations web application for the MoneyBags microservice stack. It uses Oracle JET 17.1 with Preact/TypeScript and calls every business service through the API Gateway at `http://localhost:8080`.

## Run the complete application

Start the backend services from the repository root in PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\run-all.ps1
```

`run-all.ps1` starts Eureka, Identity, all implemented business services, and the Gateway. It does not start the frontend.

Start the frontend separately in another PowerShell window:

```powershell
cd .\moneybags-web
npm run serve
```

Then open `http://localhost:8000`.

Stop everything with:

```powershell
.\stop-all.ps1
```

Local Identity users are created by the `local` profile. Unless the corresponding `.env` variables were changed, use:

- Customer: `consumer@moneybags.local` / `ChangeThisConsumerPassword!`
- Operations: `admin@moneybags.local` / `ChangeThisAdminPassword!`

These are development credentials only.

## Frontend-only development

```powershell
cd .\moneybags-web
npm install
npm run serve
```

Useful verification commands:

```powershell
npm run typecheck
npm run build
npm run build:release
```

The complete role-based functional, integration, security, resilience and accessibility test catalogue is in [`FRONTEND_TEST_CASES.md`](FRONTEND_TEST_CASES.md).

Runtime endpoints and OAuth client IDs are in `src/runtime-config.js`. The local defaults match the Gateway and Identity ports used by `run-all.ps1`.

## Security and integration

- Sign-in uses OAuth 2.0 Authorization Code with PKCE.
- Access and refresh tokens remain in memory; browser storage contains only the short-lived PKCE verifier while sign-in is in progress.
- All business requests carry the bearer token, `X-Tenant-ID`, a UUID `X-Correlation-ID`, and an `Idempotency-Key` for mutations.
- The Gateway validates the tenant/customer context and forwards trusted identity headers to services.
- Customer bills are filtered by the authenticated CIF in Bill Generation; known bill IDs are ownership checked.

## Implemented experience

Customer pages cover profile/CIF onboarding, KYC and document upload, products, deposit accounts, fixed deposits, credit-card applications/accounts, payments, card bills, account-wise recent activity, generated statement PDFs, and notifications. Operations pages cover the KYC work queue, products, journals, GL accounts, accounting rules, and subledger mappings.

Statements use Deposit Account as the authoritative transaction and balance source. Accounting enrichment remains available, but it does not block PDF generation unless strict reconciliation is enabled in Statements Service. Reconciliation and EOD pages still show an unavailable state because those standalone services are not present in this repository.
