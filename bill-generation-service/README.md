# Bill Generation Service

This standalone service runs on port `8087` and owns immutable credit-card bills in Oracle schema `MONEYBAGS_BILLING`. It uses Spring Boot 4.1.0, JDK 25, JPA and Liquibase; Flyway is not used.

Set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` for the dedicated Oracle billing user. Local default mode uses deterministic upstream stubs (`STUB_UPSTREAM_CLIENTS=true`) based on product `CC-PLAT-001`, so the included [Postman collection](postman/Bill-Generation-Service.postman_collection.json) runs without other services. Set it to `false` and configure `PRODUCT_URL`, `CREDIT_CARD_URL`, and `ACCOUNTING_URL` to call peer services.

Eureka is disabled by default so the service can run directly on port `8087`. For a full multi-service environment, start Discovery Server and set `EUREKA_ENABLED=true` (and `EUREKA_URL` if it is not `http://localhost:8761/eureka/`).

Run from the repository root with `mvn -pl bill-generation-service -am spring-boot:run`. The relevant APIs are `/internal/v1/bills/generate`, `/api/v1/bills/{billId}`, `/internal/v1/bills/{billId}/summary`, `/internal/v1/bills`, and `/internal/v1/bills/eod/close`.

After Accounting posts a successful card-payment journal, Payments calls `POST /internal/v1/bills/{billId}/payment-settlements`. The call is idempotent by `paymentId`; Bill Generation records the allocation and transitions the bill to `PARTIALLY_PAID` or `PAID`. Credit Card can call `GET /internal/v1/bills/accounts/{accountId}/closure-eligibility` to determine whether any unpaid bills block closure.
