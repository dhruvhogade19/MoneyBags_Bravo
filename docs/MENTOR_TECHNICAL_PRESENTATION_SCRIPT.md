# MoneyBags — Technical Mentor Presentation Script

**Suggested duration:** 12–15 minutes, plus 5 minutes for questions  
**Goal:** demonstrate architectural reasoning, correctness controls, and an honest view of current delivery status.

---

## Before presenting

Have these tabs ready, in this order:

1. `http://localhost:8000` — MoneyBags web application.
2. `http://localhost:8761` — Eureka registry.
3. `http://localhost:8080/actuator/gateway/routes` — gateway routes.
4. `http://localhost:8085/swagger-ui/index.html` — Payments API.
5. `http://localhost:8086/swagger-ui.html` — Deposit Account API.

Start the stack before the session with `./run-all.ps1`, and run the frontend separately from `moneybags-web` with `npm run serve`.

---

## Slide 1 — Opening: the problem and the solution

**Show:** title slide, or the landing page.

**Say:**

“Good [morning/afternoon]. This is MoneyBags, a banking-platform project built as a set of independently deployable Spring Boot services with a role-aware Oracle JET web application.

The business scope includes customer onboarding, KYC, banking products, deposit accounts and fixed deposits, credit cards, payments, billing, accounting, notifications, and statements. My focus today is not only what features were implemented, but how the technical design protects banking invariants: identity and tenant isolation, no duplicate money movement, balanced accounting, audited state transitions, and database ownership.”

**Transition:** “I’ll first show the architecture, then trace one transaction through it, and finally discuss security, persistence, testing, and the remaining production gaps.”

---

## Slide 2 — Architecture at a glance

**Show:** this diagram.

```text
Browser (Oracle JET + Preact/TypeScript)
                 |
     OAuth2/OIDC Authorization Code + PKCE
                 v
Identity Access :8093 ---- issues JWTs ----+
                                           |
                                           v
                                    API Gateway :8080
                                    /          |          \
                                   v           v           v
                         Eureka :8761    Business services  OpenAPI/Actuator
                                           |
       +-----------+----------+-------------+------------+----------------+
       |           |          |             |            |                |
      CIF         KYC    Product Master  Deposit      Payments        Credit Card
     :8081       :8082      :8083         :8086        :8085           :8084
       |           |          |             |            |                |
       +-------- Notification :8090 --- Accounting :8088 --- Billing :8087
                                                     |
                                             Statements :8089
                                                     |
                                           Oracle + Liquibase per service
```


“The public entry point is the API Gateway on port 8080. It validates the access token, accepts only public route families, and routes through Eureka rather than hard-coded addresses. The browser never calls an internal service endpoint.

There are thirteen Maven modules: Identity, Discovery, Gateway, CIF, KYC, Product Master, Deposit Account, Payments, Credit Card, Accounting, Bill Generation, Notification, and Statements. The frontend is a separate Oracle JET 17.1 application using Preact and TypeScript.

The key boundary is data ownership. Each operational service owns only its own tables and Liquibase changelog. It does not query another service’s tables or reuse its JPA entities. Service-to-service integration happens through documented authenticated HTTP contracts. Accounting is distinct because it owns the immutable financial journal, while operational services own their respective business state.”

---

## Slide 3 — What each domain service owns

**Show:** this compact ownership table.

| Domain | Service responsibility |
|---|---|
| Identity | OAuth2/OIDC users, roles, clients, JWT claims |
| CIF + KYC | Customer master data; immutable KYC case evidence and review |
| Product Master | Deposit/card catalogue, eligibility, fees, rate slabs and policy |
| Deposit | CASA/FD lifecycle, holders, limits, reservations and operational balance projection |
| Credit Card | Applications, card accounts, available limit, outstanding and authorisation holds |
| Payments | Payment intent, orchestration status, peer-call attempts, idempotency and compensation |
| Accounting | Journals, balanced lines, rules, subledger mappings, periods and reconciliation |
| Billing / Statements / Notification | Bill snapshots and allocation; immutable PDFs; delivery history/retries |

**Say:**

“This split follows bounded contexts rather than splitting arbitrarily by CRUD screens. For example, Product Master defines a product and its pricing policy; it does not open an account. Deposit opens and manages the deposit account; it does not own the general ledger. Payments coordinates multi-service movement; it is not the ledger of record.

That separation is what lets the system evolve without turning the database into a hidden monolith.”

---

## Slide 4 — Technology choices

**Show:** the stack list.



“The backend uses Java 25, Spring Boot 4.1, Spring Cloud 2025.1, Maven, Spring Data JPA, Oracle JDBC, and Liquibase. Spring Cloud Gateway plus Eureka handles service discovery and routing. Spring RestClient is used for synchronous peer calls. Each HTTP service exposes Swagger/OpenAPI and Actuator health endpoints; the project also includes Prometheus and Resilience4j dependencies.

For persistence, Oracle 19c is the compatibility baseline. Schema changes are forward-only Liquibase changesets and Hibernate runs with `ddl-auto: validate`, never `update` or `create`. For Oracle compatibility, persisted booleans use `NUMBER(1)` with Hibernate’s numeric boolean converter.

The frontend is Oracle JET 17.1 with Preact and TypeScript. It uses the gateway, not direct service URLs, and keeps access and refresh tokens in memory.”

---

## Slide 5 — Security model

**Show:** Identity login page or the security architecture diagram in `docs/SECURITY_IMPLEMENTATION.md`.

**Say:**

“Security is enforced at every trust boundary, not only at the gateway. Identity Access is the OAuth2 and OpenID Connect issuer. Human users authenticate using Authorization Code with PKCE. Services use Client Credentials for internal calls.

JWTs carry issuer, audience, roles, scopes, tenant ID and, for consumers, customer ID. The two human roles are `CONSUMER` and `BANK_ADMIN`. A consumer can access only records linked to the customer ID in the signed token; a bank administrator is restricted to the signed tenant and has governed operational capabilities.

At the gateway, caller-supplied identity headers are removed and trusted values are injected from the verified JWT. The client must send a matching tenant header and a UUID correlation ID. Internal paths are explicitly denied at the gateway. Importantly, each business service still validates issuer, audience, roles, and scopes itself, so bypassing a gateway route would not bypass authorization.”

**Mentor-level point:** “This is defense in depth: gateway authentication, service authorization, tenant checks, object ownership checks, and service scopes are separate controls.”

---

## Slide 6 — Onboarding and KYC workflow

**Show:** Customer onboarding/KYC page or process-flow diagram.

**Say:**

“A new consumer begins with an identity but may not have a CIF yet. The consumer can create one CIF exactly while the identity is unlinked. CIF binds that identity to the generated customer ID using an authenticated internal call and initiates an immutable KYC snapshot.

The customer uploads PAN, Aadhaar, address proof, and salary proof. A bank administrator reviews documents; reviewer identity comes from the JWT rather than client-supplied JSON. A final KYC decision is blocked until all four documents are present and reviewed. The result is synchronized to CIF and a notification is requested.

The important design decision is that CIF remains the customer/contact source of truth, while KYC is the evidence and decision source of truth. That makes audit evidence explicit instead of overloading the customer record.”

---

## Slide 7 — Product and account opening

**Show:** Product page, then Deposit Account Swagger or UI.

**Say:**

“Product Master owns reusable product definitions: savings, current, fixed-deposit, and credit-card products, including eligibility, fees, interest policies, and rate slabs. Deposit Account requests a customer eligibility snapshot from CIF and validates the selected product through Product Master before opening an account.

Savings and current accounts are opened through the CASA endpoint. Fixed deposits have a dedicated quote and booking flow and are funded from an eligible active deposit account. The deposit service snapshots necessary product terms at account creation, so historical accounts are not silently re-priced when product policy changes.

Deposit also enforces lifecycle transitions—activate, block, freeze, dormant, reactivate, request close, and confirm close—rather than allowing arbitrary status updates. Closure is guarded by zero-balance conditions.”

---

## Slide 8 — Main end-to-end demo: deposit-to-deposit transfer

**Show:** Payments page, then Swagger request/result if time allows.

**Say:**

“This is the central correctness flow. The customer submits a book transfer through Payments with an `Idempotency-Key`, correlation ID, tenant header, and bearer token.

First, Payments stores the intent and asks Deposit to validate both accounts and reserve the source funds. A reservation means those funds cannot be spent by a concurrent transaction. Next, Payments requests a balanced liability-to-liability journal from Accounting. Only after posting succeeds does Deposit capture the reservation: it debits the source and credits the destination within one Oracle transaction using pessimistic balance locks and immutable transaction records.

Payments then marks the payment as `SETTLED` and triggers the confirmation notification. A repeated request with the same idempotency key returns the previous result rather than moving funds again.

If Accounting reports an uncertain timeout, Payments does not assume failure. It looks up the stable external reference. If the journal was actually posted, the workflow continues; if an operational settlement fails after a journal post, the design calls for reversal and reservation release. This is a practical saga-style orchestration: there is no distributed database transaction, so correctness comes from idempotency, state machines, durable attempts, and compensation.”

**Show this concise state machine:**

```text
PENDING_VALIDATION → PENDING_RESERVATION → PENDING_ACCOUNTING
→ PENDING_SETTLEMENT → SETTLED

Exceptional: FAILED | CANCELLED | REVERSAL_PENDING | REVERSED
```

---

## Slide 9 — Accounting and auditability

**Show:** accounting UI or Swagger endpoint list.

**Say:**

“Accounting is deliberately not just another balance table. It owns journal headers and lines, posting rules, subledger mappings, reconciliation, and accounting periods. For a transfer, it creates balanced debit and credit lines; operational balance projection and financial record are therefore separate but reconcilable.

This separation gives us three forms of traceability: the business payment state in Payments, the operational balance movement in Deposit, and the immutable financial evidence in Accounting. Correlation IDs, idempotency keys, payment references, and journal references make a production incident traceable across those views.”

---

## Slide 10 — Reliability and sensitive data controls

**Say:**

“The platform applies reliability controls where banking workflows need them most. Mutable requests are idempotent. Deposit mutations use pessimistic locks and record immutable transaction history. Account state transitions are constrained; `If-Match` can support optimistic concurrency at the API boundary.

Deposit uses a transactional notification outbox: the account-change transaction and its notification command commit together, then a retryable dispatcher sends the notification after commit using a stable idempotency key. A notification failure does not roll back a completed financial operation.

For PII, account numbers are masked in APIs and logs. Nominee names are encrypted using AES-256-GCM with a 32-byte Base64 key sourced from deployment secrets. Configuration is externalized through an ignored `.env` locally and should be backed by a secret manager in production.”

---

## Slide 11 — Operability and developer experience

**Show:** Eureka dashboard and optionally `/actuator/health`.

**Say:**

“The local launcher, `run-all.ps1`, loads local environment configuration, locates JDK 25, prevents port collisions, creates per-service logs, starts services in dependency-aware order, waits for listeners, and validates Actuator health. It also limits Hikari pools for a shared developer Oracle environment and uses localhost Eureka registration to avoid stale VPN addresses.

Every service can be inspected directly through health and OpenAPI endpoints. The gateway has narrow, explicit routes—for example, `/api/v1/payments/**` to Payments and `/api/deposit-accounts/**` to Deposit—rather than a catch-all route. This makes the public API surface deliberate and reviewable.”

---

## Slide 12 — Verification evidence

**Show:** `docs/TEST_VERIFICATION_REPORT.md`.

**Say:**

“The verification report records a clean Maven reactor result of 107 tests with zero failures, 11 of 11 applications returning `UP`, and a 20-group authenticated cross-service workflow. The security audit also checked that unauthenticated direct-service and gateway operations were rejected.

The verified positive paths include PKCE authentication, CIF creation and identity linking, KYC document and review gates, product validation, deposit opening, accounting-backed transfer with idempotent replay, and credit-card merchant payment.

Tests use H2 in Oracle compatibility mode and execute the Liquibase changelog. That is useful for fast feedback, but I would be explicit that it is not a substitute for validating migrations and Hibernate mapping against the real Oracle environment.”

---

## Slide 13 — Honest current limitations and next steps

**Say:**

“This is a strong training-platform implementation, but I would not present it as production-complete. The main next steps are:

1. Standardize cross-service contracts—error envelopes, pagination, IDs, timestamps, money scale, and canonical card references.
2. Close payment recovery gaps, especially a captured-debit reversal or equivalent replay-safe recovery for a failed credit-card repayment projection.
3. Add real HTTP timeout-and-recovery integration tests, not only unit-level contract tests.
4. Persist EOD cutoff and business-date state; the current payment cutoff is in memory and resets on restart.
5. Use durable outbox processing consistently, particularly where Payments currently sends notifications synchronously.
6. Complete live authenticated end-to-end verification of the Bill Generation repayment flow.
7. For production deployment, move from the shared local service-client secret to per-client credentials or private-key JWT/mTLS, persist OAuth client registration, use a secret manager, HTTPS, private internal networking, and SIEM/audit integration.

These are not hidden weaknesses; they are the roadmap derived from understanding where distributed financial systems fail: timeouts, partial completion, incompatible contracts, and recovery after restart.”

---

## Slide 14 — Close

**Say:**

“To summarize, MoneyBags is built around domain ownership and controlled integration. The gateway and OAuth layer establish identity; services enforce tenant, role, scope, and ownership rules; Liquibase and Oracle provide controlled persistence; Payments coordinates distributed workflows using idempotency and compensation; and Accounting preserves immutable financial evidence.

The main lesson from this project is that a banking microservice system is not complete when each API works independently. It is complete only when the cross-service flow remains safe under retries, concurrency, timeout uncertainty, and audit review. That is the standard the architecture is designed around, and the remaining roadmap is focused on closing the gaps to that standard. Thank you—I’m happy to walk through the transfer flow, security model, or database ownership in more detail.”

---

## Likely mentor questions and concise answers

| Question | Answer |
|---|---|
| Why microservices instead of one application? | The domains have different ownership, lifecycle and security needs. The tradeoff is distributed-systems complexity, so the design uses explicit contracts, idempotency and compensations rather than pretending it has one transaction. |
| How do you prevent duplicate transfers? | A client supplies an `Idempotency-Key`; Payments persists the result and Deposit also uses payment references/reservations. A retry returns or resumes the existing operation rather than applying another debit. |
| How is a partially completed transfer handled? | The payment state records the stage. Unknown Accounting timeouts are resolved by lookup using the external reference. Failed settlement triggers release/reversal logic. One repayment compensation gap remains on the stated roadmap. |
| Why separate Deposit balances from Accounting? | Deposit needs an operational balance projection for authorization and lifecycle actions. Accounting needs an immutable, balanced financial journal. Separation supports independent responsibility and reconciliation. |
| Is gateway security enough? | No. The gateway is the public ingress, but every business service is also a JWT resource server and enforces roles, scopes, tenant and ownership checks. |
| Why Liquibase? | It gives reviewable, ordered, reproducible schema evolution and supports clean database boot. Hibernate validates rather than mutates production schema. |
| Why synchronous HTTP and not Kafka? | The current workflows require immediate validations and outcomes, so Spring RestClient plus explicit state/recovery is simpler for this scope. Kafka is intentionally not included; durable outbox/eventing is a logical future evolution for asynchronous work. |
| What is the biggest production risk today? | Cross-service recovery consistency: complete captured-debit compensation, persist EOD state, and validate timeout/retry behavior with real integration tests. |

## Optional 6-minute version

If time is short, present slides 1, 2, 5, 8, 10, 12, 13 and 14. Keep the transfer walkthrough, security defense-in-depth, verification evidence, and honest next steps; they convey the most technical maturity.
