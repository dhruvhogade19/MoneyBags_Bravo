# Moneybags Accounting Service

The Accounting service is the authoritative double-entry book for Moneybags. It accepts complete financial facts
from Payments, Bill Generation, and Deposit Account, resolves Accounting-owned rules and mappings, and stores
immutable balanced journals. It also supplies ledger inquiries, account lifecycle clearance, trial balances,
financial reconciliation, and Accounting-period controls.

## Runtime

- Java 25
- Spring Boot 4.1.0 and Spring Cloud 2025.1.2
- Direct port: `8088`
- Eureka name: `accounting-service`
- Oracle schema/user: `MONEYBAGS_ACCOUNTING`
- Runtime database: Oracle only (there is no runtime H2 profile)
- Swagger: `http://localhost:8088/swagger-ui.html`
- Health: `http://localhost:8088/actuator/health`

Accounting has no normal outbound REST dependency. Source services push complete financial facts to its
`/internal/v1/**` APIs. Only `/api/v1/**` inquiry and administration paths are routed by the API Gateway.

## Oracle setup

Create a dedicated user in the same PDB used by the Moneybags services. Run the following as an Oracle DBA,
substituting a real secret and an appropriate tablespace/quota for the environment:

```sql
CREATE USER MONEYBAGS_ACCOUNTING IDENTIFIED BY "<strong-password>";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO MONEYBAGS_ACCOUNTING;
ALTER USER MONEYBAGS_ACCOUNTING QUOTA UNLIMITED ON USERS;
```

Set these local `.env` values:

```properties
MONEYBAGS_ACCOUNTING_DB_URL=jdbc:oracle:thin:@//localhost:1522/FREEPDB1
MONEYBAGS_ACCOUNTING_DB_USERNAME=MONEYBAGS_ACCOUNTING
MONEYBAGS_ACCOUNTING_DB_PASSWORD=<strong-password>
EUREKA_URL=http://localhost:8761/eureka/
EUREKA_ENABLED=true
SECURITY_ENABLED=true
OAUTH2_JWK_SET_URI=http://localhost:8081/.well-known/jwks.json
```

Liquibase creates the schema and seeds the initial INR chart, mappings, and rule versions. Hibernate uses
`ddl-auto=validate`; it never creates or updates Accounting tables.

## Security

Security is enabled by default.

- Internal service APIs require `SCOPE_accounting:service`.
- Public inquiry APIs require `SCOPE_accounting:read` or `SCOPE_accounting:admin`.
- Administrative mutations require `SCOPE_accounting:admin`.

For an isolated developer machine only, `SECURITY_ENABLED=false` allows Swagger and Postman testing without an
identity provider. Do not use that setting in a shared or deployed environment.

## Account registration rollout

`ACCOUNTING_ENFORCE_ACCOUNT_REGISTRATION=false` is the compatibility default while Deposit and Credit Card add
opening notifications. Accounting still rejects a posting when a matching registered account is already `CLOSED`.
Set `ACCOUNTING_ENFORCE_ACCOUNT_REGISTRATION=true` after existing account references have been bootstrapped and both
account services reliably call `POST /internal/v1/account-lifecycle-events`.

Account opening and closing facts never create a journal. The account-owning service controls operational status;
Accounting controls only its subledger projection and Accounting clearance.

## Posting guarantees

- Every posted journal has positive, equal debit and credit totals.
- Posted journals and lines are immutable.
- Corrections use a new opposite journal.
- A stable external reference, `Idempotency-Key`, and canonical request hash prevent duplicate postings.
- Reusing a reference with different content, or reusing one key for another reference, returns `409`.
- A caller that times out uses the appropriate `by-reference` endpoint before retrying.
- A closed Accounting period rejects new postings.

`CREDIT_CARD_MERCHANT_PAYMENT` uses rule `CREDIT_CARD_MERCHANT_PAYMENT_PRINCIPAL` version 1. It debits
`CREDIT_CARD_RECEIVABLE` for the source card account and credits `MERCHANT_PAYABLE` for the required `merchantId`.
This journal creates a payable; actual merchant settlement is a separate future workflow.

## EOD control refresh

Trial-balance and financial-reconciliation requests accept an optional positive `executionEpoch` (default `1`).
An exact epoch is an idempotent, request-validated replay. A higher epoch creates a fresh immutable snapshot and
audits the supersession of the prior active snapshot; stale attempts with changed content return `409`. Period-close
and dashboard blockers evaluate only the active snapshot. Payment and fixed-deposit reconciliation controls share
the real EOD run ID and are isolated by their `stepCode` (or a service-derived discriminator for legacy requests),
so refreshing one control never supersedes the other. EOD can read period state through
`GET /internal/v1/accounting-periods/{businessDate}` with `SCOPE_accounting:service`; the existing public inquiry
route and the invariant that a closed period cannot be reopened are unchanged.

## Build and test

From the repository root:

```powershell
mvn -pl accounting-service -am test
```

To run only this service after configuring Oracle:

```powershell
mvn -pl accounting-service spring-boot:run
```

The fast integration tests use test-scoped H2 in Oracle compatibility mode. H2 is not packaged into or used by the
running Accounting service. The tests verify Liquibase startup, balanced posting, idempotent replay/key-conflict
protection, original-journal refunds, lifecycle closure protection, trial balance, and period closure.

To run the same integration suite against a disposable Oracle test schema, set the following variables before the
test command. Never point this test path at a production schema because the suite creates persistent Accounting data.

```powershell
$env:ACCOUNTING_TEST_DB_URL = "jdbc:oracle:thin:@//localhost:1522/FREEPDB1"
$env:ACCOUNTING_TEST_DB_USERNAME = "MONEYBAGS_ACCOUNTING_TEST"
$env:ACCOUNTING_TEST_DB_PASSWORD = "<test-schema-password>"
$env:ACCOUNTING_TEST_DB_DRIVER = "oracle.jdbc.OracleDriver"
mvn -pl accounting-service -am test
```

Oracle 19c is the compatibility baseline; the Oracle test run must apply Liquibase successfully and pass Hibernate
schema validation before deployment.

## Postman

Import `postman/Accounting-Service.postman_collection.json`. Set `accountingBaseUrl`, `bearerToken`, and
`businessDate`. The collection opens deterministic test subledgers before testing postings, so use a clean test
schema or change the identifiers between runs.

The detailed API and flow contract is in
`../docs/synchronous-contracts/accounting-service-api-contract.md`.
