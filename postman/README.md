# Moneybags Postman workflow

`Moneybags-Complete-Workflow.postman_collection.json` is the platform-level collection. It combines a deterministic cross-service workflow with namespaced copies of the detailed service collections already maintained by each module.

## What folders 00-05 verify

1. Every required service is ready, including Identity Access, CIF, and Credit Card.
2. Product Master, Deposit Account and Accounting Liquibase seed data exists.
3. A book transfer flows through Payments, Deposit, Accounting and Notification with idempotent replay.
4. Deposit source and target balances move exactly once and Accounting creates a balanced journal.
5. KYC is created, supplied with a document, verified and approved.
6. Notification persistence, replay and idempotency conflict behavior are verified.
7. Negative requests prove validation and ProblemDetail behavior.

Folder `90` contains complete controller coverage for Identity Access, CIF, Payments, Product Master, Credit Card and the KYC notification callback, followed by namespaced copies of the existing Deposit, Deposit/Product integration, Accounting, Product, Notification and Credit Card collections. Select its child folders explicitly: some requests create policies, run EOD controls, close accounts, or otherwise mutate deterministic seed records.

The **Identity and CIF** folder is the secured onboarding flow: it creates a disposable consumer identity, creates and updates a CIF from an unlinked consumer JWT, checks the CIF views consumed by Deposit, Credit Card and Notification, synchronizes the KYC state, and verifies the private identity-to-CIF link. Set `adminBearerToken`, `consumerBearerToken`, `serviceBearerToken`, and `postmanUserPassword` before running it. The consumer token must belong to an unlinked consumer for the CIF-create request; obtain a fresh consumer token after linking when testing the customer-facing downstream APIs.

## Seed data

The workflow uses application-owned seed mechanisms rather than ad-hoc SQL:

- Product Master: Liquibase product catalogue (`SAV-REG-001`, `FD-REG-001`, credit-card products and pricing rules).
- Deposit Account: Liquibase contexts `testdata,postman`, including `seed-sav-source-001` and `seed-cur-target-001`.
- Accounting: Liquibase chart of accounts, mappings and posting rules. The local launcher uses isolated H2 when dedicated Oracle credentials are absent.
- Notification: Liquibase notification templates.
- KYC: unique records are created through its API during the run.
- `Moneybags-Workflow.postman_data.json`: Collection Runner/Newman iteration data for the happy-path scenario.

Do not point this collection at production. It performs real state changes.

## Run in Postman Desktop

1. Start the platform with `run-all.ps1` and confirm every listed service is `UP`.
2. Import the collection and `Moneybags-Local.postman_environment.json`.
3. Set Postman's working directory to the repository root so `postman/fixtures/kyc-pan.pdf` can be uploaded.
4. Run folders `00` through `05` in order. Use `Moneybags-Workflow.postman_data.json` as the data file if using Collection Runner.
5. Run individual folders under `90` only when their extra destructive coverage is required.

The optional Payments requests for merchant purchase, card repayment, fixed-deposit funding and fixed-deposit payout are automatically skipped until their prerequisite collection variables are populated. Set `creditCardAccountId`, `billId`, `fixedDepositId`, and `fixedDepositAccountId` as applicable. Credit Card coverage uses `credit-card-service` on port `8087`, which is included in `run-all.ps1`.

The collection generates a timestamp run ID, UUID correlation ID, unique PAN/Aadhaar test identifiers, and idempotency keys automatically. Clear the collection variable `runId` before starting another run in the same imported collection.

To regenerate all three Postman artifacts after an API or seed change:

```powershell
node .\postman\generate-moneybags-collection.mjs
```

## Run with Newman

From the repository root:

```powershell
newman run .\postman\Moneybags-Complete-Workflow.postman_collection.json `
  -e .\postman\Moneybags-Local.postman_environment.json `
  -d .\postman\Moneybags-Workflow.postman_data.json `
  --working-dir .
```

For repeat runs against persistent Oracle, Deposit's seeded funding account has ample headroom, but destructive requests in folder `90` may require resetting the relevant local schema.
