# Moneybags Deposit Account Service

Spring Boot implementation of the Moneybags Deposit Account bounded context. The directory layout mirrors the supplied FundWise multi-module Maven reference while replacing MySQL with an Oracle-owned schema and implementing the Deposit Account design.

## Modules

| Module | Port | Responsibility |
|---|---:|---|
| `discovery-server` | 8761 | Eureka service registry |
| `api-gateway` | 8080 | Routes deposit-account APIs to the service |
| `deposit-account-service` | 8086 | Savings/current account lifecycle plus payment reservation, settlement and balance projection |

The project intentionally does not include Product Master, CIF, KYC, Payment, Statement or Reconciliation implementations. They are separate bounded contexts. For local development, CIF and Product Master validation is stubbed; production mode calls them through OpenFeign and Eureka.

## Technology

- Java 25
- Spring Boot 4.1.0, Spring Cloud 2025.1.2 and Maven
- Spring Data JPA with Oracle JDBC
- Liquibase Oracle migrations
- OAuth2 resource-server/JWT support
- Eureka, Spring Cloud Gateway and OpenFeign
- OpenAPI/Swagger, Actuator, Prometheus and Resilience4j

Kafka, Docker and Flyway are intentionally not used.

## Oracle setup

The default connection is:

```text
jdbc:oracle:thin:@//localhost:1521/FREEPDB1
username: moneybags_deposit
password: moneybags_deposit
```

Create a dedicated Oracle user/schema, then provide the connection settings below. Liquibase creates the service tables when the application starts:

```powershell
$env:DB_URL = "jdbc:oracle:thin:@//db-host:1521/SERVICE_NAME"
$env:DB_USERNAME = "moneybags_deposit"
$env:DB_PASSWORD = "use-a-secret-manager"
```

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
2. `DepositAccountServiceApplication`
3. `ApiGatewayApplication`

Useful URLs:

- Eureka: `http://localhost:8761`
- Gateway API: `http://localhost:8080/api/deposit-accounts`
- Service Swagger UI: `http://localhost:8086/swagger-ui.html`
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
$env:PRODUCT_URL = "http://localhost:8084"
```

When OpenFeign OAuth2 propagation is required, also configure the client registration and enable it:

```powershell
$env:FEIGN_OAUTH2_ENABLED = "true"
$env:FEIGN_OAUTH2_CLIENT_REGISTRATION_ID = "moneybags-service-client"
```

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
| `POST /api/deposit-accounts` | Open an account; requires `Idempotency-Key` |
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

- Only Savings and Current products are accepted. FD/RD products are rejected.
- Accounts open with a zero balance. Initial funding, branch/IFSC validation, cash deposits, external inbound credits, interest, fees, reversals and statement/notification/reconciliation events are deferred.
- This service owns account identity, holders, lifecycle, limits, payment reservations, book-transfer settlement, card-repayment capture and the balance projection.
- Payment mutations use pessimistic balance locks and immutable transaction records; book-transfer debit and credit commit in one Oracle transaction.
- Account aggregate changes, audit evidence and idempotency results share the same Oracle transaction.
- Account numbers are masked in API responses and logs.
- Every mutable command is protected by state-transition rules; `If-Match` can enforce optimistic concurrency.
- Closure requires zero ledger, available and blocked balances. Production should add explicit clearance events from Transaction and Reconciliation before `confirm-close` is authorized.

## Database migrations

The Liquibase changelog is:

```text
deposit-account-service/src/main/resources/db/changelog/db.changelog-master.yaml
```

Never use `ddl-auto=update` against Oracle. Runtime configuration uses `ddl-auto=validate`; all schema changes must be forward Liquibase changesets.

The runnable Postman collection is at `deposit-account-service/postman/Deposit-Account-Service.postman_collection.json`.
