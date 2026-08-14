# Moneybags Banking System

Spring Boot services for the Moneybags banking platform. Each service owns its Oracle schema and integrates with peers through HTTP APIs registered in Eureka.

## Modules

| Module | Port | Responsibility |
|---|---:|---|
| `discovery-server` | 8761 | Eureka service registry |
| `api-gateway` | 8080 | Gateway routes for Product Master and Deposit Account APIs |
| `product-master-service` | 8083 | Deposit and credit-card product catalogue, eligibility and fixed-deposit pricing rules |
| `deposit-account-service` | 8086 | CASA and fixed-deposit lifecycle, payment reservations, balance projections and notification outbox |
| `accounting-service` | 8088 | Accounting journals, posting rules and financial ledger operations |

The workspace currently contains the five services above. CIF, KYC, Payments, Statements, Reconciliation and Notification Service remain peer bounded contexts outside this repository. Deposit uses stubbed CIF/Product Master validation by default; production mode resolves configured peers through Spring `RestClient` and Eureka. Accounting is started by `run-all.ps1`; it is currently built independently rather than included in the root Maven reactor.

## Technology

- Java 25
- Spring Boot 4.1.0, Spring Cloud 2025.1.2 and Maven
- Spring Data JPA with Oracle JDBC
- Liquibase Oracle migrations
- OAuth2 resource-server/JWT support
- Eureka, Spring Cloud Gateway and Spring RestClient
- OpenAPI/Swagger, Actuator, Prometheus and Resilience4j

Kafka, Docker and Flyway are intentionally not used.

## Oracle setup

The default connection is:

```text
jdbc:oracle:thin:@//localhost:1521/FREEPDB1
username: moneybags_deposit
password: moneybags_deposit
```

Create a dedicated Oracle user/schema, then put the connection settings in `.env` at the project root. Liquibase creates the service tables when the application starts:

```dotenv
DB_URL=jdbc:oracle:thin:@//db-host:1521/SERVICE_NAME
DB_USERNAME=moneybags_deposit
DB_PASSWORD=use-a-secret-manager
```

`run-all.ps1` loads this ignored file into its process before starting the services.

Do not grant other microservices direct access to this schema.

## Build and test

From the project root:

```powershell
mvn clean verify
```

Tests use H2 in Oracle compatibility mode and execute the same Liquibase changelog. Install JDK 25 and make sure `java -version` and `mvn -version` both report it before building.

## Run locally

1. Start Oracle.
2. Build the project.
3. Run all modules:

```powershell
.\run-all.ps1
```

Or start them from IntelliJ in this order:

1. `DiscoveryServerApplication`
2. `ProductMasterServiceApplication`
3. `DepositAccountServiceApplication`
4. `AccountingServiceApplication`
5. `ApiGatewayApplication`

Useful URLs:

- Eureka: `http://localhost:8761`
- Gateway API: `http://localhost:8080/api/deposit-accounts`
- Product Master Swagger UI: `http://localhost:8083/swagger-ui.html`
- Service Swagger UI: `http://localhost:8086/swagger-ui.html`
- Accounting Swagger UI: `http://localhost:8088/swagger-ui.html`
- Health: `http://localhost:8086/actuator/health`

Stop launcher-created processes with `./stop-all.ps1`.

## Local and production modes

Local defaults are deliberately convenient:

- `SECURITY_ENABLED=false`
- `STUB_UPSTREAM_CLIENTS=true`

Production must set:

```powershell
$env:SECURITY_ENABLED = "true"
$env:STUB_UPSTREAM_CLIENTS = "false"
$env:SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI = "https://identity.example/issuer"
$env:CIF_URL = "https://cif.internal"
$env:PRODUCT_MASTER_URL = "http://product-master-service"
$env:PRODUCT_MASTER_ACCESS_TOKEN = "service-token-from-secret-manager"
$env:NOTIFICATION_URL = "http://notification-service"
$env:NOTIFICATION_DISPATCH_ENABLED = "true"
```

Spring RestClient propagates the correlation ID for upstream calls. Product Master validation uses
`POST /internal/v1/products/{productCode}/validate-account-opening`; its access token must come from the
deployment secret manager when Product Master security is enabled.
For FD pricing, Deposit uses `customerCategory` from CIF when present; until CIF publishes that optional field,
it derives `SENIOR_CITIZEN` at age 60 or above and `REGULAR` otherwise from the trusted date of birth.

### Deposit notifications

Deposit persists notification commands in its own `NOTIFICATION_OUTBOX` table within the same transaction as the banking change. A retryable dispatcher calls Notification Service `POST /internal/v1/notifications` after commit. It sends:

- `DEPOSIT_ACCOUNT_CREATED` when a CASA account is opened or an FD account is booked.
- `FD_MATURITY` after an FD payout completes.

Each outbound request has a stable `Idempotency-Key` (`deposit-account-{accountId}-created` or `fd-{accountId}-maturity`). Notification Service must return HTTP `200 OK`; failed calls remain pending and are retried using `NOTIFICATION_DISPATCH_DELAY` (default `5000` ms). Set `NOTIFICATION_DISPATCH_ENABLED=false` when a Notification Service is deliberately unavailable, such as isolated local tests.

Nominee names are encrypted with AES-256-GCM. Configure a 32-byte Base64 key before sending nominee data:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:MONEYBAGS_PII_KEY_BASE64 = [Convert]::ToBase64String($bytes)
```

Store that key in the deployment secret manager; rotating it requires a governed re-encryption process.

## Main APIs

| Method and path | Purpose |
|---|---|
| `POST /api/deposit-accounts` | Open a Savings or Current account; requires `Idempotency-Key` |
| `POST /api/deposit-accounts/fixed-deposits` | Book a funded fixed deposit; requires `Idempotency-Key` |
| `POST /api/deposit-accounts/fixed-deposits/quotes` | Quote fixed-deposit terms and maturity amount |
| `GET /api/deposit-accounts/fixed-deposits/{fdId}` | Get a fixed-deposit account |
| `POST /internal/v1/deposit-accounts/eod/fixed-deposit-accruals` | Accrue fixed-deposit interest for an EOD business date |
| `POST /internal/v1/deposit-accounts/eod/fixed-deposit-maturities` | Complete eligible fixed-deposit maturity payouts |
| `POST /api/deposit-accounts/eligibility-check` | Validate CIF/KYC/product without writing |
| `GET /api/deposit-accounts/{id}` | Detailed masked account view |
| `GET /api/deposit-accounts?customerId=&status=` | Paged account search |
| `GET /api/deposit-accounts/{id}/balance` | Accounting-fed balance projection |
| `POST /api/deposit-accounts/{id}/holders` | Add an eligible holder |
| `DELETE /api/deposit-accounts/{id}/holders/{customerId}` | Remove a non-primary holder |
| `PUT /api/deposit-accounts/{id}/nominees` | Replace nominee instructions |
| `PUT /api/deposit-accounts/{id}/limits/{type}` | Create/update an account limit |
| `POST /api/deposit-accounts/{id}/mandates` | Add an authorized mandate |
| `DELETE /api/deposit-accounts/{id}/mandates/{mandateId}` | Revoke a mandate |
| `POST /api/deposit-accounts/{id}/commands/{command}` | Lifecycle transition |
| `GET /api/deposit-accounts/{id}/status-history` | Immutable lifecycle history |
| `GET /api/internal/deposit-accounts/{id}/eligibility` | Peer-service debit/credit eligibility |
| `POST /api/internal/deposit-payment-operations/book-transfers/reservations` | Reserve source funds for a book transfer |
| `POST /api/internal/deposit-payment-operations/book-transfers/{paymentId}/settlement` | Atomically debit source and credit target |
| `POST /api/internal/deposit-payment-operations/card-repayments/reservations` | Reserve funds for a card repayment |
| `POST /api/internal/deposit-payment-operations/card-repayments/{paymentId}/capture` | Capture a reserved card-repayment debit |
| `POST /api/internal/deposit-payment-operations/{paymentId}/release` | Release an active reservation |
| `GET /api/internal/deposit-payment-operations/{paymentId}` | Read payment-operation status and transaction IDs |

Supported lifecycle commands: `activate`, `block`, `unblock`, `freeze`, `release-freeze`, `mark-dormant`, `reactivate`, `request-close`, and `confirm-close`.

### Open-account example

```powershell
$headers = @{
  "Content-Type" = "application/json"
  "Idempotency-Key" = "opening-10001"
  "X-Correlation-Id" = "branch-request-10001"
}
$body = @{
  customerIds = @("CIF-100245")
  primaryCustomerId = "CIF-100245"
  productId = "prod-savings-standard"
  productVersion = 7
  currency = "INR"
  openingAmount = 0
  servicingBranchId = "BR-0012"
  operatingInstruction = "SINGLE"
  nominees = @()
  channel = "BRANCH"
  externalReference = "origination-72819"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/deposit-accounts" `
  -Headers $headers -Body $body
```

## Important design choices

- Savings and Current accounts can be opened directly. Fixed deposits are booked through the dedicated FD API and funded from an eligible active Deposit account.
- CASA accounts open with a zero balance. Cash deposits, external inbound credits, fees, reversals, statements and reconciliation integrations remain outside the current scope.
- This service owns account identity, holders, lifecycle, limits, payment reservations, book-transfer settlement, card-repayment capture and the balance projection.
- Payment mutations use pessimistic balance locks and immutable transaction records; book-transfer debit and credit commit in one Oracle transaction.
- Account aggregate changes, audit evidence and idempotency results share the same Oracle transaction.
- Notification outbox entries share the same transaction as account creation, FD booking and FD maturity payout; notification delivery is retried independently.
- Account numbers are masked in API responses and logs.
- Every mutable command is protected by state-transition rules; `If-Match` can enforce optimistic concurrency.
- Closure requires zero ledger, available and blocked balances. Production should add explicit clearance events from Transaction and Reconciliation before `confirm-close` is authorized.

## Database migrations

The Liquibase changelog is:

```text
deposit-account-service/src/main/resources/db/changelog/db.changelog-master.yaml
```

Never use `ddl-auto=update` against Oracle. Runtime configuration uses `ddl-auto=validate`; all schema changes must be forward Liquibase changesets.

Postman collections:

- `deposit-account-service/postman/Deposit-Account-Service.postman_collection.json` covers the Deposit Account API.
- `deposit-account-service/postman/Deposit-Product-Master-Integration.postman_collection.json` verifies the live Deposit Account -> Product Master integration, including savings eligibility/opening, FD slab resolution/booking, persisted product snapshots, and negative decisions.

Run the integration collection in folder order with `STUB_UPSTREAM_CLIENTS=false`. Discovery, Gateway, Product
Master, Deposit Account, CIF, and Oracle must be available. For local processes, set `PRODUCT_MASTER_URL` and
`CIF_URL` to their reachable localhost URLs. The collection defaults to Product Master on port `8083`, Deposit
Account on `8086`, Gateway on `8080`, and CIF on `8081`; override its collection variables if your ports differ.
Bearer-token variables may remain empty while security is disabled. Each successful run creates a savings
account and books an INR 1,000 fixed deposit from `seed-sav-source-001`, so reruns mutate seeded account state.

The complete current endpoint catalog, request/response JSON templates, service-wise dependencies, and production contract gaps are documented in [`docs/Deposit_Account_Service_API_and_Dependencies.md`](docs/Deposit_Account_Service_API_and_Dependencies.md).
