# Moneybags Product Master Service

Product Master owns the reusable product catalogue only. It does not open accounts, issue cards, store balances, or change Deposit Account Service interactions.

## Run and test

Copy the root `.env.example` to `.env` and supply the shared Oracle connection. From the repository root, `./run-all.ps1` now starts Product Master on port `8083` and the gateway exposes it at `http://localhost:8080/api/products`.

- Swagger UI (direct): `http://localhost:8083/swagger-ui.html`
- OpenAPI JSON (direct): `http://localhost:8083/v3/api-docs`
- Gateway catalogue: `http://localhost:8080/api/products/category/CREDIT_CARD/active`
- Postman collection: `postman/Product-Master-Service.postman_collection.json`

The root `.env` is optional and ignored by Git. Services locate it whether their working directory is the repository root or the individual service module. `MONEYBAGS_DB_URL`, `MONEYBAGS_DB_USERNAME`, and `MONEYBAGS_DB_PASSWORD` are read by stateful services. Legacy service-specific database variables remain supported as fallbacks.

## Catalogue model

The generic `PRODUCT`/`PRODUCT_INTEREST_RULE` persistence model is replaced by readable roots:

```text
DEPOSIT_PRODUCT
  -> DEPOSIT_INTEREST_POLICY
  -> DEPOSIT_PRODUCT_FEE / ELIGIBILITY / FEATURE
  -> FIXED_DEPOSIT_RATE_SLAB (FD only)

CREDIT_CARD_PRODUCT
  -> CREDIT_CARD_TERMS
  -> CREDIT_CARD_INTEREST_POLICY
  -> CREDIT_CARD_PRODUCT_FEE / ELIGIBILITY / FEATURE
```

`DEPOSIT_PRODUCT` has dedicated named columns for amount, closure, premature-closure, renewal, and FD configuration. Savings/current products return `fixedDepositRule`, `prematureClosureRule`, and `renewalRule` as `null`, and `interestRateSlabs` as `[]`.

Boolean product fields use Oracle-compatible `NUMBER(1)` storage (`1` = true, `0` = false). The JPA model explicitly converts these values and does not rely on native SQL `BOOLEAN`, which keeps the schema compatible with Oracle 19c and Oracle AI Database 26ai.

## Seed catalogue

- Savings: `SAV-REG-001`
- Current: `CUR-BIZ-001`
- Fixed deposits: `FD-REG-001`, `FD-FLEX-001`
- Credit cards: `CC-PLAT-001`, `CC-GOLD-001`

All public product responses retain `version: 1`. Product and policy versioning is intentionally not implemented.

Liquibase retires the old `PRODUCT_INTEREST_RULE` and `PRODUCT` tables after seeding the replacement catalogue. The cleanup changesets use table-exists preconditions: on a clean production database they are marked as already run; on a legacy schema they run in dependency order.

## New compact card catalogue

`GET /api/products/category/CREDIT_CARD/active/minimal` returns the compact active card catalogue. `GET /api/products/{productCode}/minimal` returns the same compact shape for one credit-card code. Both return only product code, name, annual interest rate, the active eligibility rule, and concise customer-facing messages. The individual minimal route returns `400` for deposit products; the full product endpoint remains unchanged.

## Fixed-deposit calculations

- **Daily accrual** calculates earned interest for each day.
- **ACTUAL_365** uses actual calendar days divided by 365.
- **Compound interest** adds posted interest to principal so later interest can earn interest.
- **Quarterly compounding** compounds every three months.
- **At maturity posting** credits accumulated FD interest at maturity.
- **Booking-date rate** locks the chosen rate at FD opening; renewal can use the maturity-date rate instead.
- **Premature closure** uses the matching actual-tenure rate minus the configured penalty, subject to the minimum payable interest rate.

The Product Master rate quote selects an active FD slab by amount, tenure, and effective date. Account lifecycle and actual daily accrual posting remain Deposit Account Service responsibilities.
