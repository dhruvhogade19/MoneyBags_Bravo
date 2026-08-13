# Moneybags contributor guide

This is the repository-level instruction file for Codex and a practical onboarding guide for every team member adding a microservice. Read it before creating a module or changing a shared integration.

## Ground rules

- This repository uses Maven multi-module builds, Java 25, Spring Boot 4.1.0, Spring Cloud 2025.1.2, Oracle, Liquibase, Eureka, and Springdoc OpenAPI.
- A service owns its tables and Liquibase changelog. Do not reach into another service's tables, add cross-service foreign keys, or depend on another service's JPA entities.
- Integrate across services through documented HTTP endpoints (or the agreed event contract when one is added), not through the shared database.
- Preserve existing API paths and response contracts unless the owning team explicitly agrees on a versioned contract change. `version` in product responses is intentionally hardcoded to `1`; do not introduce optimistic/versioned API behaviour for it.
- Never commit `.env`, passwords, database exports, generated `target/` output, or log files.

## Add a microservice

1. Create a top-level module directory, for example `demo-service`.
2. Give its `pom.xml` the root Maven parent:

   ```xml
   <parent>
       <groupId>com.moneybags</groupId>
       <artifactId>moneybags</artifactId>
       <version>0.0.1-SNAPSHOT</version>
       <relativePath>../pom.xml</relativePath>
   </parent>

   <artifactId>demo-service</artifactId>
   ```

3. Register it in the root [`pom.xml`](pom.xml) under `<modules>`:

   ```xml
   <module>demo-service</module>
   ```

4. Choose a unique application name and port according to this(names here are generic follow proper naming convention):
identity : 8081,
   KYC : 8082,
   product-master : 8083,
   credit card : 8084,
   Payments : 8085,
   deposit creation : 8086,
   bill generation : 8087,
   accounting : 8088,
   statements : 8089,
   notification : 8090,
   reconcilation : 8091,
   discovery service : 8761,
   gateway service : 8080 
5. Add them to the service `application.yml`, then add the service to [`run-all.ps1`](run-all.ps1) so local startup, port collision checks, logs, and health checks include it.
5. Add an API Gateway route in [`api-gateway/src/main/resources/application.yml`](api-gateway/src/main/resources/application.yml). Use the Eureka service name, for example `uri: lb://demo-service`, and a narrow `Path=` predicate. Do not create a catch-all route.
6. Add Swagger/OpenAPI (`springdoc-openapi-starter-webmvc-ui`) for HTTP services and verify `/swagger-ui.html` and `/v3/api-docs` directly on that service's port.
7. Add a Postman collection or extend the shared collection for the service's public endpoints.
8. Build and test from the repository root:

   ```powershell
   mvn -pl demo-service -am test
   ```

## Shared configuration and local database

Copy [`.env.example`](.env.example) to a local root `.env` and set the real values. Every stateful service must use these shared names first:

```properties
MONEYBAGS_DB_URL=jdbc:oracle:thin:@//<hostname>:<portname>/<dbname>
MONEYBAGS_DB_USERNAME=<username>
MONEYBAGS_DB_PASSWORD=<password>
EUREKA_URL=http://localhost:8761/eureka/
EUREKA_ENABLED=true
```

Use this pattern in `src/main/resources/application.yml`. The two config import locations deliberately support starting the service from either the repository root or its own module directory.

```yaml
spring:
  config:
    import: optional:file:.env[.properties],optional:file:../.env[.properties]
  application:
    name: demo-service
  datasource:
    url: ${MONEYBAGS_DB_URL:${CARD_ACCOUNT_DB_URL:jdbc:oracle:thin:@//localhost:1522/FREEPDB1}}
    username: ${MONEYBAGS_DB_USERNAME:${CARD_ACCOUNT_DB_USERNAME:moneybags}}
    password: ${MONEYBAGS_DB_PASSWORD:${CARD_ACCOUNT_DB_PASSWORD:moneybags}}
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml

eureka:
  client:
    enabled: ${EUREKA_ENABLED:true}
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
```

Keep optional service-specific variables only as local-development fallbacks. The `.env` values are the coordinated connection used by all services.

## Oracle and Liquibase rules

- Oracle 19c is the compatibility baseline. Write migrations that also run on Oracle AI Database 26ai; do not rely on newer Oracle-only features.
- Use Liquibase for every schema change. Keep `ddl-auto: validate`; never use JPA `update` or `create` against a shared database.
- Oracle has no SQL table-column `BOOLEAN` in 19c. Store booleans as `NUMBER(1)` (`1` = true, `0` = false) and map them explicitly in Hibernate:

  ```java
  import jakarta.persistence.Column;
  import jakarta.persistence.Convert;
  import org.hibernate.type.NumericBooleanConverter;

  @Convert(converter = NumericBooleanConverter.class)
  @Column(name = "ACTIVE", nullable = false, columnDefinition = "NUMBER(1)")
  private boolean active;
  ```

  `columnDefinition = "NUMBER(1)"` alone is not enough: without the converter, Hibernate can still validate the field as a native boolean and fail at startup.
- Prefer Oracle-safe SQL types used by the existing changelogs: `VARCHAR2`, `NUMBER(p,s)`, `DATE`, `TIMESTAMP`, and `CHAR(36)`/the established UUID representation. Be precise about scale for money and rates.
- Treat migration drops, truncates, and data transformations as production operations. Back up first, include preconditions, and obtain agreement from the data owner.

## Product Master boundaries

Product Master owns reusable catalogue definitions only: deposit products (savings/current/fixed deposit), credit-card products, eligibility, fees, features, interest policies, and rate slabs.

- It does **not** open or close accounts, issue cards, hold balances, post interest, or manage deposits' lifecycle.
- Deposit Account Service remains independent. Do not add Product Master database-level dependencies to it.
- The generic legacy `PRODUCT` and `PRODUCT_INTEREST_RULE` model was retired in favour of `DEPOSIT_PRODUCT` and `CREDIT_CARD_PRODUCT` roots. Do not recreate the generic table.
- Product Master Swagger is direct at `http://localhost:8083/swagger-ui.html`; its gateway API is under `http://localhost:8080/api/products`.

## Before opening a pull request

- Run the module test suite and the root build path that includes your module.
- Verify a clean database boot applies your Liquibase migrations and passes Hibernate schema validation.
- Verify the service registers in Eureka when enabled, its gateway route works, `/actuator/health` returns successfully, and Swagger loads.
- Test happy path, validation failure, not-found behaviour, and the gateway path in Postman.
- Document public endpoints, required environment variables, and migrations in the service README.
- Do not change a neighbouring service merely to make a local test pass. Coordinate contract changes with that service's owner.

## Troubleshooting seen in this repository

| Symptom | Likely cause and fix |
|---|---|
| `ORA-01017: invalid username/password` | Connect to the same PDB named in the JDBC URL (normally `FREEPDB1`), not `CDB$ROOT`. Ensure `.env` contains the actual password without quotes; `MONEYBAGS_DB_PASSWORD=root`, not `"root"`. Check for OS environment variables overriding `.env`. |
| User is active but the service cannot log in | An Oracle user may have been created in a different container/PDB. In SQL*Plus, `show con_name`; create/unlock the user in `FREEPDB1` when the URL ends in `/FREEPDB1`. |
| `Schema-validation: wrong column type ... found NUMBER ... expecting BOOLEAN` | Apply `NumericBooleanConverter` to every Java boolean persisted in Oracle and use `NUMBER(1)` in the Liquibase migration. Rebuild and restart the service. |
| Service cannot see root `.env` | Keep both imports: `optional:file:.env[.properties],optional:file:../.env[.properties]`. The first handles repository-root launches; the second handles module-directory launches. |
| Maven says the Java release is unsupported | This project requires JDK 25. Set `JAVA_HOME` to the JDK 25 installation, or use `./run-all.ps1`, which locates it under `C:\Program Files\Java`. |
| Gateway returns 404 while direct service works | Add the narrow route in the Gateway config, use `lb://` plus the exact `spring.application.name`, and ensure both Eureka and the target service are running. |
| Service starts but a Product Master route returns no seeded products | Check Liquibase logs and `DATABASECHANGELOG`; the service needs the shared DB user to have the required privileges. Avoid manually changing Liquibase history. |
| Swagger UI is unavailable | Confirm `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` are true, then use the service's direct port. The gateway does not currently aggregate Swagger UIs. |
| Tests pass on H2 but Oracle fails | H2 is useful for application tests but is not an Oracle compatibility guarantee. Validate migrations and JPA mappings against Oracle before merging. |

