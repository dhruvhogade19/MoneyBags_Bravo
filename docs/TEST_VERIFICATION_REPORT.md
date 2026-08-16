# Moneybags verification report

Verified locally on 2026-08-16 with security enabled and real service-to-service clients.

## Result

- Complete Maven reactor: **PASS** (`107` tests, `0` failures, `0` errors, `0` skipped).
- Service health: **PASS** (`11/11` applications returned `HTTP 200` and `UP`).
- Authenticated cross-service workflow: **PASS** (`20/20` workflow groups).
- API-wide unauthenticated-access audit: **PASS** (`164/164` direct operations and `121/121` gateway operations rejected requests without a JWT).
- Postman collection and environment: valid JSON.

The authenticated workflow verifies:

1. OAuth 2.0 Authorization Code with PKCE for bank-admin and consumer users.
2. Gateway JWT validation, roles, scopes, tenant headers and consumer ownership.
3. Bank-admin creation of a consumer identity.
4. Consumer CIF creation, asynchronous identity linking and immutable KYC snapshot creation.
5. Consumer upload of PAN, Aadhaar, address proof and salary proof.
6. Admin work queue, per-document `VERIFIED`/`MISMATCH`, `FLAGGED`, premature-decision rejection and final approval.
7. Final KYC synchronization to CIF and persisted customer notification.
8. Product read and service-only product validation.
9. CIF/Product-backed deposit eligibility, opening, activation and Accounting lifecycle registration.
10. Book transfer through Deposit, Payments, Accounting and Notification, including idempotent replay and exact-once balance validation.
11. CIF/Product/Accounting-backed credit-card application and account opening.
12. Credit-card merchant payment through Card, Payments, Accounting and Notification, including idempotent replay.

## Bill Generation follow-up

The report above predates the Bill Generation service integration. Bill Generation is now assigned port `8087`, is registered with Eureka, is routed through the gateway for customer bill reads, and supports authenticated service-to-service lookup and payment-settlement callbacks. Its live end-to-end repayment workflow still needs to be run against the full local stack.

The API-wide sweep proves authentication is enforced for every operation discovered from the running OpenAPI documents. It does not claim that every destructive business-state mutation was executed with a successful payload; positive-path coverage is supplied by the Maven suites, Postman collection and the 20-group live workflow above.

## Re-run

From the repository root:

```powershell
mvn test
.\postman\scripts\run-live-verification.ps1
```
