# MoneyBag delivery scope, gap register, and UI structure

Status captured: 17 August 2026.

## Release position

The current application has the necessary authentication and navigation provisions for the two requested human roles:

- `BANK_ADMIN` is the banker role.
- `CONSUMER` is the technical role name used for the customer/self-service experience.

The OJET UI, `ui-bff`, Identity provider, Gateway routes, role/scope checks, customer onboarding guard, and public catalogue path are present. This is suitable for the current local demonstration and integration cycle. It is **not production-ready** until the P0 hardening items below are closed.

Statement generation and cross-service EOD orchestration are intentionally pending. Their pages describe the boundary and do not pretend that those services exist.

## Delivered access and UI flow

### Browser security boundary

The browser has one origin, `http://localhost:8000`, served by [`ui-bff`](../ui-bff):

- OAuth 2.0/OIDC Authorization Code with PKCE uses separate public clients, `moneybags-consumer` and `moneybags-admin`.
- OAuth access and refresh tokens remain server-side; the browser receives a session cookie and CSRF cookie/token. Identity and the BFF use distinct cookie names so their localhost sessions cannot overwrite each other during the authorization redirect.
- `GET /api/session` exposes only the UI session view, roles, onboarding state, login links, and CSRF state.
- `/api/proxy/api/**` forwards authenticated public Gateway calls and injects the bearer token, trusted tenant/customer context, a UUID correlation ID, and idempotency keys. `/internal/**` paths are rejected. For customers, the BFF performs a live CIF check and denies banking calls until KYC is `APPROVED`; only profile, KYC, notification, and catalogue operations are allowed beforehand.
- `GET /api/public/products` and `GET /api/public/products/{code}` expose only active catalogue entries. Nested catalogue internals, unpublished products, and all writes remain unavailable anonymously; product mutations require `product:admin` and the admin UI guard.
- Gateway CORS defaults to the BFF origin in [`api-gateway/src/main/resources/application.yml`](../api-gateway/src/main/resources/application.yml); OIDC redirects target port 8000 in [`identity-access-service/src/main/resources/application.yml`](../identity-access-service/src/main/resources/application.yml).

Primary implementation locations are [`ui-bff/src/main/java/com/moneybags/uibff`](../ui-bff/src/main/java/com/moneybags/uibff), [`ui-bff/src/main/resources/application.yml`](../ui-bff/src/main/resources/application.yml), [`moneybags-ui/src/js/services/auth/session.js`](../moneybags-ui/src/js/services/auth/session.js), and [`moneybags-ui/src/js/viewModels/appController.js`](../moneybags-ui/src/js/viewModels/appController.js).

### Customer onboarding and access

```text
Public catalogue
  -> self-register email/password
  -> Identity creates CONSUMER with PENDING_PROFILE and no customer_id
  -> customer signs in and creates CIF profile
  -> CIF linkage starts KYC
  -> customer signs in again so a fresh token carries customer_id
  -> customer uploads KYC documents
  -> BANK_ADMIN verifies documents and approves KYC
  -> approved-only banking routes become available
```

Before approval, a customer can use Profile, KYC, Notifications, and active-product reads. Deposit accounts, fixed deposits, cards, payments, bills, statements, and the customer dashboard are guarded as `customer-approved` in the router and at the BFF boundary. Domain services still enforce ownership and role/scope rules; the UI guard is not treated as the security boundary.

### Delivered OJET modules

| Area | Routes/modules |
|---|---|
| Public | Landing, product catalogue, product detail, registration, Security, About |
| Customer onboarding | Profile/CIF, KYC documents and status, notifications |
| Customer banking | Dashboard, deposit accounts, fixed deposits, credit-card application/accounts, payments, bills |
| Bank administration | Operations dashboard, Customer 360, KYC queue/review, deposit operations, credit-card operations |
| Product Master | Catalogue, product editor, pricing/interest policies, benchmark rates; admin-only writes |
| Bank operations | Payment operations, billing lookup, accounting/GL, EOD cockpit, IAM |
| Explicit pending UI | Customer Statements; EOD orchestration steps |

The existing Vela design variables remain in place; the navbar/header override is pink in [`moneybags-ui/src/css/theme-presets.css`](../moneybags-ui/src/css/theme-presets.css).

## Project structure

### Runnable Maven modules

| Module | Port | Responsibility |
|---|---:|---|
| `discovery-server` | 8761 | Eureka discovery |
| `api-gateway` | 8080 | Narrow public routes, JWT enforcement, trusted headers, CORS |
| `ui-bff` | 8000 | OJET hosting, OIDC client, server-side token/session, CSRF, safe proxy |
| `identity-access-service` | 8093 | Identity provider, users, registration, roles, OAuth clients |
| `cif-service` | 8081 | Customer profile and KYC status snapshot |
| `kyc-service` | 8082 | KYC cases, documents, verification and decisions |
| `product-master-service` | 8083 | Public deposit/card catalogue and admin product maintenance |
| `credit-card-service` | 8084 | Card applications, accounts and card lifecycle |
| `payments-service` | 8085 | Transfers, repayments and payment orchestration |
| `deposit-account-service` | 8086 | Deposit accounts and fixed deposits |
| `bill-generation-service` | 8087 | Credit-card bill generation, lookup and settlement |
| `accounting-service` | 8088 | Journals, GL, rules, periods, trial balance and reconciliation |
| `notification-service` | 8090 | Customer notifications and delivery |

The canonical module list is in [`pom.xml`](../pom.xml), and the coordinated local startup order/profiles are in [`run-all.ps1`](../run-all.ps1). That script also produces a fresh optimized OJET build before starting the BFF, preventing stale UI assets from being served.

### UI and BFF layout

```text
moneybags-ui/
  src/index.html                         role-aware pink navigation and workspace shells
  src/css/
    app.css                              existing Vela-based application styling
    theme-presets.css                    light/dark tokens and pink header override
    workspaces.css                       shared public/customer/admin workspace styling
  src/js/
    viewModels/appController.js          routes and public/customer/admin guards
    services/auth/session.js             BFF session and onboarding state
    services/api/
      http.js, client.js, gatewayApi.js  same-origin transport and aggregate adapter
      customerApi.js, productApi.js
      depositApi.js, cardApi.js
      paymentApi.js, notificationApi.js
      accountingApi.js, identityApi.js   domain adapters
    viewModels/ and views/
      public/                            landing, products, detail, registration,
                                         Security and About content
      customer/                          dashboard, profile, KYC, accounts, FDs,
                                         cards, payments, bills, notifications, statements
      banker/                            dashboard, customers, KYC, accounts, cards,
                                         catalogue/editor/pricing/benchmarks, payments,
                                         billing, accounting, EOD, IAM

ui-bff/
  src/main/java/com/moneybags/uibff/
    auth/                                session and anonymous registration facade
    config/                              OIDC, CSRF, HTTP and static-resource configuration
    proxy/                               authenticated and public-product proxy policy
    api/, http/                          safe upstream exchange and error handling
  src/main/resources/application.yml     port, session, OIDC scopes, Gateway/Identity URLs
```

Detailed adapter contracts are recorded in [`moneybags-ui/CUSTOMER_UI_API_CONTRACT.md`](../moneybags-ui/CUSTOMER_UI_API_CONTRACT.md) and [`moneybags-ui/src/js/viewModels/banker/API_CONTRACT.md`](../moneybags-ui/src/js/viewModels/banker/API_CONTRACT.md).

## Local access seeds

The `local` Identity profile seeds these development-only users:

| Experience | Username | Default password |
|---|---|---|
| Bank admin | `admin@moneybags.local` | `ChangeThisAdminPassword!` |
| Approved customer | `customer@moneybags.local` | `ChangeThisConsumerPassword!` |
| Compatibility customer | `consumer@moneybags.local` | `ChangeThisConsumerPassword!` |

The customer identities link to local CIF `101`; the CIF `local` Liquibase context seeds that record as KYC `APPROVED`. Passwords are overridden by `LOCAL_ADMIN_PASSWORD` and `LOCAL_CONSUMER_PASSWORD`. These defaults must never be used outside a developer workstation. See [`identity-access-service/src/main/resources/application-local.yml`](../identity-access-service/src/main/resources/application-local.yml) and [`cif-service/src/main/resources/db/changelog/changes/006-local-customer-seed.yaml`](../cif-service/src/main/resources/db/changelog/changes/006-local-customer-seed.yaml).

## Gap register

### Intentionally pending for this delivery

| ID | Gap | Current safe behaviour | Completion boundary |
|---|---|---|---|
| `STMT-01` | Statement Service backend is absent from the Maven reactor, Gateway, and `run-all.ps1`. | The customer Statements page is visible but generation/download is disabled; EOD labels statements as pending. | Add a schema-owning Statement module, Liquibase, secured public/internal contracts, Gateway route, and generated-file lifecycle. |
| `EOD-01` | There is no cross-service EOD orchestrator module. | The admin EOD page performs read-only card readiness, accounting-period, trial-balance, and reconciliation lookups. It never invokes internal mutation APIs. | Add durable run state, ordered steps, retries/resume, idempotency, operator authorization, audit trail, and failure recovery across providers. |

### Production hardening required

| Priority | ID | Gap and evidence | Required action |
|---|---|---|---|
| P0 | `DATA-01` | Deposit, Accounting, and Bill Generation each define service-local tables named `IDEMPOTENCY_RECORD` and `AUDIT_LOG`, with incompatible shapes in their [Deposit](../deposit-account-service/src/main/resources/db/migration/V1__create_deposit_account_schema.sql), [Accounting](../accounting-service/src/main/resources/db/changelog/changes/001-create-accounting-schema.sql), and [Billing](../bill-generation-service/src/main/resources/db/changelog/changes/001-billing-schema.sql) migrations. A single shared Oracle username/schema will collide. | Provision a separate Oracle schema/user per service, or agree prefixed table migrations and a controlled data migration. Do not deploy these changelogs together under one schema owner. |
| P0 | `IAM-01` | Identity explicitly uses `InMemoryRegisteredClientRepository` in [`AuthorizationServerConfig`](../identity-access-service/src/main/java/com/moneybags/identity/config/AuthorizationServerConfig.java); OAuth authorization/consent state is not configured for durable JDBC storage. A restart loses server-side authorization state. | Persist registered clients, authorizations, and consents using Spring Authorization Server JDBC repositories and Liquibase-owned tables. |
| P0 | `IAM-02` | All machine-to-machine OAuth clients share `M2M_CLIENT_SECRET`. | Issue and rotate a separate secret per client, or move to private-key JWT/mTLS with a secret manager. |
| P0 | `EOD-02` | [`EodControlService`](../payments-service/src/main/java/com/moneybags/payments/service/EodControlService.java) holds Payments cutoff in an `AtomicBoolean` and a volatile date. A process restart can lose the cutoff state. | Persist the business-date control state and coordinate it through the future EOD run. |
| P0 | `QA-01` | Module tests primarily use H2; [`run-all.ps1`](../run-all.ps1) also selects H2-backed `local` profiles for Identity and Accounting and mock mail for Notifications. There is no committed automated OJET end-to-end suite or evidence in-repo of a clean full-stack Oracle browser run. | Run clean Oracle migrations/schema validation for every service, then automate both role journeys through BFF/Gateway against the coordinated stack. |
| P1 | `IAM-03` | MFA, email verification, password reset/recovery, login throttling/rate limiting, lockout, and device/session administration are not implemented. | Add risk-appropriate Identity and Gateway controls before public access. |
| P1 | `OPS-01` | Local defaults use HTTP, a non-secure BFF session cookie, localhost CORS, and demonstration credentials/secrets. | Terminate TLS, set `UI_SESSION_COOKIE_SECURE=true`, configure the exact production origin, rotate every credential, and use managed secrets. |

### Stubs and release limitations

| ID | Location | Current behaviour | Release action |
|---|---|---|---|
| `STUB-01` | [`deposit-account-service/src/main/resources/application.yml`](../deposit-account-service/src/main/resources/application.yml) | `moneybags.deposit.stub-upstream-clients` defaults to `true`; CIF, Product Master and Accounting integrations can silently use stubs in a standalone launch. | Set `STUB_UPSTREAM_CLIENTS=false` for integrated environments and verify real contracts. `run-all.ps1` already does this. |
| `STUB-02` | [`credit-card-service/src/main/resources/application.yml`](../credit-card-service/src/main/resources/application.yml) | `moneybags.credit-card.stub-upstream-clients` also defaults to `true`; reference, accounting, and notification gateways have stub implementations. | Set `STUB_UPSTREAM_CLIENTS=false` outside isolated tests and verify real calls. `run-all.ps1` already does this. |
| `STUB-03` | [`bill-generation-service/src/main/resources/application.yml`](../bill-generation-service/src/main/resources/application.yml) | Bill upstream stubbing exists but defaults to `false`; notification stubbing follows the shared flag unless separately overridden. | Keep false for integrated environments; use stubs only in isolated tests. |
| `STUB-04` | [`notification-service/src/main/java/com/moneybags/notification/notification/integration/MockEmailSender.java`](../notification-service/src/main/java/com/moneybags/notification/notification/integration/MockEmailSender.java) | Coordinated local startup selects the `mock-mail` profile, so notification records are exercised but no real email provider is verified. | Configure and test the real mail adapter in an integration environment. |
| `EOD-03` | [`deposit-account-service/src/main/java/com/moneybags/deposit/service/DepositEodService.java`](../deposit-account-service/src/main/java/com/moneybags/deposit/service/DepositEodService.java) | CASA daily accrual records an idempotent control result but deliberately posts zero interest. Fixed-deposit EOD is separate and implemented. | Add product-rate resolution, accrual persistence, accounting posting, balancing and retry/recovery before enabling CASA accrual. |
| `DOC-01` | [`CreditCardController`](../credit-card-service/src/main/java/com/moneybags/creditcard/controller/CreditCardController.java) | Several OpenAPI descriptions still say authentication is not enforced, although [`SecurityConfig`](../credit-card-service/src/main/java/com/moneybags/creditcard/config/SecurityConfig.java) now applies scopes, roles, and ownership checks. | Correct the stale descriptions so generated API documentation matches runtime security. |

## Release acceptance checklist

- Use `./run-all.ps1` so real upstream clients are selected and the local Identity/CIF seeds load.
- Prove anonymous catalogue browsing and rejection of anonymous product writes.
- Prove self-registration -> profile -> KYC pending, then admin approval -> fresh sign-in -> banking access.
- Prove a customer can open a deposit account, apply for a credit card, make a payment/repayment, and read only their own bills.
- Prove Bank Admin can review KYC and operate Product Master, deposits, cards, payments, billing, accounting, IAM, and the read-only EOD cockpit.
- Keep Statement generation and EOD mutations disabled until `STMT-01` and `EOD-01` are implemented.
- Do not promote to production until every P0 item is closed and verified against Oracle.

Product response `version` remains intentionally hardcoded to `1` per the repository contract; it is not an unfinished optimistic-locking feature.
