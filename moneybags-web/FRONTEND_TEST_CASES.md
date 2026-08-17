# MoneyBags frontend end-to-end test cases

## 1. Purpose and scope

This specification tests the Oracle JET frontend from workspace selection through every role-appropriate customer and bank-operations function. It covers UI behaviour, OAuth 2.0 Authorization Code with PKCE, API Gateway integration, authorization boundaries, business-service orchestration, empty/loading/error states, accessibility, responsive behaviour, and intentionally unavailable modules.

The browser under test is `http://localhost:8000`. Business requests must go through `http://localhost:8080`; the frontend must not call business-service ports directly. Identity is hosted at `http://localhost:8093`.

## 2. Roles and expected route boundaries

| Actor | Expected area | Allowed behaviour | Must not be allowed |
|---|---|---|---|
| Anonymous | `/` and Identity login | Choose a workspace and authenticate | View customer or operations content |
| Consumer | `/app/**` | Access only the signed `customer_id` and its products, accounts, cards, payments, bills, KYC and notifications | Access `/ops/**`, another customer, Accounting administration or KYC review |
| Bank administrator | `/ops/**` | Review tenant-scoped queues, products, portfolios and Accounting data | Enter customer pages as if they were a consumer or mutate customer-owned data without an admin contract |
| Machine client | No browser workspace | Service-to-service calls only | Interactive frontend sign-in |

## 3. Test personas and fixtures

Run destructive or mutation cases only against a disposable local/test database.

| Fixture | Required state |
|---|---|
| `ANON` | Clean browser context with no MoneyBags session |
| `C-EMPTY` | Consumer identity linked to a valid CIF; no accounts, deposits, cards, payments, bills or notifications; no KYC record |
| `C-PENDING` | Consumer with a pending KYC record and no financial products |
| `C-ACTIVE` | Approved KYC; one active deposit account with sufficient funds; one second deposit account; one fixed deposit; one active credit card; one generated bill; payments and notifications |
| `C-OTHER` | A different tenant-compatible customer used to test ownership denial |
| `ADMIN` | `BANK_ADMIN` identity in tenant `moneybags` |
| Products | At least one active savings/current product, fixed-deposit product and credit-card product; one inactive product |
| Card reference | Credit-card account whose canonical external reference is `CC-101` |
| Failure controls | Ability to stop one service, delay a response, return a validation problem and return an empty page |

Record the actual generated CIF, account, fixed-deposit, application, card, bill, payment and journal identifiers in the execution report. Do not reuse mutation idempotency keys between unrelated tests.

## 4. Release gates

Run the dependency-free regression preflight before this manual suite:

```powershell
cd moneybags-web
npm test
npm run typecheck
npm run build
```

The preflight covers safe money/date handling, stable command idempotency, OIDC logout construction and nested operations navigation. The role and service-integration cases below remain authoritative manual tests against the running stack.

- All P0 cases pass.
- No open P1 defect permits cross-customer access, cross-role access, duplicate money movement, fabricated financial data, token leakage or an incorrect success message.
- Every list/detail page renders one of: data, a meaningful empty state, loading, or an actionable error state. Blank panels and empty table shells are failures.
- Statement, operations payment search, Reconciliation/EOD and full Access Administration pass only when they show the documented unavailable/backend-contract state; fabricated results are failures.
- Mutations are verified in both the UI and the authoritative owning service.

## 5. Authentication and session cases

| ID | Pri | Role | Scenario and steps | Expected result |
|---|---|---|---|---|
| AUTH-001 | P0 | Anonymous | Open `/`. | MoneyBags landing page renders both workspace choices; no business API is called. |
| AUTH-002 | P0 | Anonymous | Select **Customer banking** and inspect the authorization request. | Redirects to Identity; client is `moneybags-consumer`; redirect URI is the frontend; `state`, `code_challenge` and `code_challenge_method=S256` are present; consumer scopes contain no admin scope. |
| AUTH-003 | P0 | Anonymous | Select **Bank operations** and inspect the authorization request. | Client is `moneybags-admin`; requested scopes include the required operations scopes; PKCE and state are present. |
| AUTH-004 | P0 | Consumer | Complete customer login with valid credentials. | Authorization code is exchanged once; URL query is removed; consumer is routed to `/app/overview`. |
| AUTH-005 | P0 | Admin | Complete operations login with valid credentials. | Admin is routed to `/ops/overview`; consumer-only controls are absent. |
| AUTH-006 | P1 | Anonymous | Submit an incorrect username or password. | The themed Identity page remains visible and shows a non-revealing authentication error; no token is issued. |
| AUTH-007 | P1 | Anonymous | Start login and cancel/deny it at Identity. | Frontend returns to the landing page with an actionable sign-in error and permits another attempt. |
| AUTH-008 | P0 | Anonymous | Alter the callback `state` before returning to the frontend. | Login is rejected as an invalid sign-in response; no session is created. |
| AUTH-009 | P0 | Anonymous | Open a callback containing `code` without the pending PKCE item. | Login is rejected; application does not attempt an unverifiable token exchange. |
| AUTH-010 | P0 | Any human | Inspect browser storage after successful login. | Access and refresh tokens are absent from Local Storage, Session Storage and cookies; the temporary PKCE item has been removed. |
| AUTH-011 | P0 | Any human | Allow the access token to approach expiry while a refresh token is valid, then open a data page. | A single refresh request occurs; the original action resumes with the new access token. |
| AUTH-012 | P0 | Any human | Expire the session and make refresh fail. | Session is cleared; protected data is no longer displayed; the user is returned to sign-in with an expiry message. |
| AUTH-013 | P0 | Any human | Use a token with the wrong audience or without `tenant_id`. | The frontend rejects the session/request; no business call is accepted as authenticated. |
| AUTH-014 | P1 | Any human | Select the avatar/sign-out control and then use browser Back. | Session is cleared; the landing page remains; protected content is not restored from browser history. |
| AUTH-015 | P1 | Anonymous | Double-click a workspace choice during redirect. | Choice buttons disable; only one authorization flow is initiated. |
| AUTH-016 | P1 | Any human | Refresh the browser after login. | Because tokens are memory-only, the user safely returns to the landing/sign-in journey; no stale protected data flashes. |

## 6. Shell, routing and navigation cases

| ID | Pri | Role | Scenario and steps | Expected result |
|---|---|---|---|---|
| NAV-001 | P0 | Consumer | Open every consumer navigation item. | Correct route, page title and active navigation state are shown. |
| NAV-002 | P0 | Admin | Open every operations navigation item. | Correct operations route, title and active navigation state are shown. |
| NAV-003 | P0 | Consumer | Enter `/ops/overview` directly. | User is redirected to `/app/overview`; no operations data is rendered. |
| NAV-004 | P0 | Admin | Enter `/app/overview` directly. | User is redirected to `/ops/overview`; no customer data is rendered. |
| NAV-005 | P1 | Any human | Enter an unknown route inside the allowed area. | Styled 404 state appears and **Back to overview** returns to the correct role overview. |
| NAV-006 | P1 | Any human | Use global search with a matching and non-matching term. | Matching role-appropriate pages are shown; selecting one navigates; an unmatched term shows “No matching MoneyBags page.” |
| NAV-007 | P1 | Any human | Use browser Back/Forward after several in-app navigations. | Route, title, data and selected navigation item remain synchronized. |
| NAV-008 | P1 | Any human | Test the mobile menu at narrow width. | Menu opens above a scrim, selecting a route closes it, and keyboard focus remains usable. |

## 7. Consumer overview, profile and KYC

| ID | Pri | Fixture | Scenario and steps | Expected result |
|---|---|---|---|---|
| CON-001 | P0 | `C-ACTIVE` | Open Overview. | Name, aggregate available balance, fixed-deposit principal, card outstanding, profile/KYC state and recent payments match service responses. |
| CON-002 | P1 | `C-EMPTY` | Open Overview. | Zero counts are accurate and an explicit **Nothing to show yet** state is visible; no fabricated activity appears. |
| CON-003 | P1 | `C-ACTIVE` | Make one of the six overview services fail. | Available information remains visible and a partial-service warning appears. |
| CON-004 | P0 | Unlinked consumer | Open Profile. | Profile onboarding form appears instead of an error or blank page. |
| CON-005 | P0 | Unlinked consumer | Submit a valid profile. | CIF is created once, Identity is linked to the returned CIF, and reauthentication produces a token containing the new `customer_id`. |
| CON-006 | P1 | Unlinked consumer | Submit missing/invalid name, date, email, mobile, PAN, Aadhaar, salary and address values. | Browser/server validation prevents creation; authoritative field errors are shown without losing entered values. |
| CON-007 | P1 | `C-ACTIVE` | Open Profile. | Personal data is correct; PAN/Aadhaar are masked; KYC status is shown; no other customer data appears. |
| KYC-001 | P0 | `C-EMPTY` | Open KYC and start verification. | A KYC snapshot is created for the signed CIF and the page changes from start state to the verification timeline. |
| KYC-002 | P0 | `C-PENDING` | Upload an accepted PDF, PNG and JPEG document. | Multipart request succeeds, success feedback names the document type, and no manual `Content-Type` boundary is supplied by the frontend. |
| KYC-003 | P1 | `C-PENDING` | Attempt an unsupported, empty or oversized document. | Upload is rejected with an actionable error; no success message appears. |
| KYC-004 | P1 | `C-PENDING` | Open a pending/rejected/approved KYC record. | Timeline, status badge, review date and rejection reason accurately reflect the record. |
| KYC-005 | P0 | Consumer | Manipulate the request to another CIF or KYC ID. | Gateway/service returns 403/404 and the UI displays an error without leaking the other customer’s data. |

## 8. Products and deposit-account cases

| ID | Pri | Fixture | Scenario and steps | Expected result |
|---|---|---|---|---|
| PROD-001 | P0 | Products seeded | Open Products. | Only active products appear with correct category, subtype, currency, description and pricing facts. |
| PROD-002 | P1 | Empty catalogue | Open Products. | **No active products** is displayed; there is no empty card grid or table shell. |
| PROD-003 | P0 | Products seeded | Choose savings/current, fixed-deposit and credit-card products. | Routes respectively to Account Open, Fixed Deposit Open and Card Apply; selected product code is prefilled. |
| ACC-001 | P0 | `C-ACTIVE` | Open Accounts. | Only signed-customer accounts appear with masked number, product, status, currency and authoritative available balance. |
| ACC-002 | P1 | `C-EMPTY` | Open Accounts. | **No deposit accounts** appears with an Open Account action. |
| ACC-003 | P0 | `C-ACTIVE` | Open an account detail. | Detail request uses the selected ID; scalar fields render; ownership is enforced. |
| ACC-004 | P1 | Empty detail payload | Open account detail. | **Nothing to show** appears instead of a blank description list. |
| ACC-005 | P0 | Eligible consumer/product | Submit Account Open with valid amount, currency and branch. | Product version is retrieved; eligibility runs first; one account is created with an idempotency key; receipt links back to Accounts. |
| ACC-006 | P0 | Ineligible consumer/product | Submit Account Open. | Eligibility/business reason is shown; account creation is not attempted. |
| ACC-007 | P1 | Any consumer | Submit zero/negative/invalid amount, missing product or invalid currency. | Client/server validation is shown; no false success receipt appears. |
| ACC-008 | P0 | Eligible consumer | Double-submit or retry after a timeout. | One logical account is created; idempotent replay returns the original result. |
| ACC-009 | P0 | Consumer | Replace an account ID with one owned by `C-OTHER`. | Access is denied and no detail or balance is leaked. |

## 9. Fixed-deposit cases

| ID | Pri | Fixture | Scenario and steps | Expected result |
|---|---|---|---|---|
| FD-001 | P0 | `C-ACTIVE` | Open Fixed Deposits. | Principal, currency, rate, status, maturity date and expected maturity amount match Deposit Service. |
| FD-002 | P1 | `C-EMPTY` | Open Fixed Deposits. | **No fixed deposits** appears. |
| FD-003 | P0 | Eligible product/account | Request a valid quote. | Product version and entered terms are sent; annual rate, expected interest and maturity amount come from the backend. |
| FD-004 | P1 | Any consumer | Quote below minimum amount, invalid tenure, unsupported payout frequency/date or inactive product. | Backend validation is visible; confirm action is unavailable. |
| FD-005 | P0 | Valid quote | Confirm booking with valid funding/payout accounts. | One fixed deposit is booked, funding orchestration completes as designed, and receipt/reference is displayed. |
| FD-006 | P0 | Valid quote | Change a quote-driving value after quoting, then attempt booking. | A stale quote is not silently used; the user must obtain a quote matching the booking values. |
| FD-007 | P0 | Consumer | Use funding/payout account belonging to `C-OTHER`. | Booking is denied; no funds or fixed deposit are created. |

## 10. Credit-card and billing cases

| ID | Pri | Fixture | Scenario and steps | Expected result |
|---|---|---|---|---|
| CARD-001 | P0 | `C-ACTIVE` | Open Cards & Bills. | Active cards show masked card number, product, limit, outstanding amount and status; applications show correct state. |
| CARD-002 | P1 | `C-EMPTY` | Open Cards & Bills. | Separate **No credit cards** and **No card applications** states appear. |
| CARD-003 | P0 | Eligible consumer/product | Submit a card application with a valid requested limit. | Application is created for the signed CIF; receipt shows application ID and status. |
| CARD-004 | P1 | Any consumer | Submit missing/inactive product or limit outside the product policy. | Validation/eligibility error is shown and no success receipt appears. |
| CARD-005 | P0 | Consumer | Manipulate CIF or card account to `C-OTHER`. | Access is denied and foreign card/application data is never rendered. |
| BILL-001 | P0 | `C-ACTIVE` | Select **View bills**. | Routes to `/app/bills`; only bills belonging to the signed consumer are listed. |
| BILL-002 | P1 | `C-EMPTY` | Open Bills. | **No bills available** appears. |
| BILL-003 | P0 | Generated unpaid bill | Open Pay Bill, enter a valid funding account and pay. | Payments receives signed customer ID, bill ID, deposit account and canonical card reference `CC-<id>`; successful response shows receipt/status. |
| BILL-004 | P0 | Generated bill | Pay the minimum/partial amount. | Payment, card outstanding and bill `paidAmount/outstandingAmount/status` agree after completion. |
| BILL-005 | P0 | Fully paid/zero bill | View bill. | Pay control is disabled or backend rejects another payment; no duplicate Accounting posting occurs. |
| BILL-006 | P0 | Consumer | Open/pay a known bill owned by `C-OTHER`. | Bill Generation ownership check denies access before payment orchestration. |

## 11. Payment cases

| ID | Pri | Fixture | Scenario and steps | Expected result |
|---|---|---|---|---|
| PAY-001 | P0 | `C-ACTIVE` | Open Payments. | Only the signed customer’s payments appear with reference, type, status, time, currency and amount. |
| PAY-002 | P1 | `C-EMPTY` | Open Payments and Overview recent activity. | Both locations show **No payments yet**; no empty table is rendered. |
| PAY-003 | P0 | Funded source/valid target | Submit a valid internal transfer. | Deposit reservation/capture, Accounting posting and notification complete; receipt shows final or submitted status and payment ID. |
| PAY-004 | P0 | Any consumer | Submit amount `0`, negative, too many decimals, missing accounts, same source/target or unsupported currency. | Validation prevents or rejects payment; no reservation/journal is left behind. |
| PAY-005 | P0 | Insufficient source balance | Submit transfer. | Payment fails with clear reason; reservation is released; no settled payment or unbalanced journal exists. |
| PAY-006 | P0 | Valid transfer | Double-click submit/retry with the same idempotency key. | One payment and one journal are created; replay returns the original result. |
| PAY-007 | P0 | Accounting timeout after commit | Submit transfer while Accounting response times out but lookup returns `status=POSTED`. | Payments treats the operation as posted, records the journal number and does not double-post. |
| PAY-008 | P0 | Accounting timeout before commit | Submit transfer while Accounting lookup is not posted. | Payment does not report false success; compensation/retry state is accurate. |
| PAY-009 | P0 | Merchant-capable card | Execute merchant payment through the API-backed flow when UI support is added. | Accounting destination contains `instrumentType=MERCHANT` and `merchantId`; credit creates merchant payable rather than immediate settlement. Mark N/A until the UI exposes this form. |
| PAY-010 | P0 | Card payment/repayment | Inspect all downstream references. | Credit Card, Payments, Billing and Accounting consistently use canonical `CC-101`; bare `101` is used only when calling a service endpoint that requires numeric ID. |
| PAY-011 | P0 | Any consumer | Attempt transfer from an account owned by `C-OTHER`. | Authorization/business ownership check rejects it; no balance or journal changes. |

## 12. Notifications and unavailable customer modules

| ID | Pri | Fixture | Scenario and steps | Expected result |
|---|---|---|---|---|
| NOTIF-001 | P1 | `C-ACTIVE` | Open Updates after a completed operation. | Notification subject, body, type, time and delivery status match Notification Service. |
| NOTIF-002 | P1 | `C-EMPTY` | Open Updates. | **No updates** appears. |
| NOTIF-003 | P0 | Consumer | Request notifications for `C-OTHER`. | Access is denied and foreign message content is not exposed. |
| STAT-001 | P1 | Consumer | Open Statements. | Page explicitly says Statement Service is unavailable; no statement totals/downloads are fabricated. |

## 13. Bank-operations cases

| ID | Pri | Role/data | Scenario and steps | Expected result |
|---|---|---|---|---|
| OPS-001 | P0 | `ADMIN` | Open Operations Overview. | KYC count and recent-journal count match services; platform state is Online or Degraded based on request outcomes. |
| OPS-002 | P1 | `ADMIN`, empty data | Open Operations Overview. | Counts are zero and Recent Journals shows **No journals found**. |
| OPS-003 | P0 | `ADMIN` | Open KYC Queue. | Tenant-appropriate review cases render with customer, status, decision and initiated date. |
| OPS-004 | P1 | `ADMIN`, empty queue | Open KYC Queue. | **Queue is clear** appears without an empty table shell. |
| OPS-005 | P0 | `ADMIN` | Approve a pending KYC case. | Decision uses authenticated reviewer identity; row refreshes to approved; CIF synchronization and notification are observable. |
| OPS-006 | P0 | `ADMIN` | Reject a case, first cancel the reason prompt and then submit a reason. | Cancel performs no mutation; confirmed rejection persists reason and updates downstream status. |
| OPS-007 | P0 | Consumer token | Call/open KYC review functions. | UI route is blocked and backend returns 403 if called directly. |
| OPS-008 | P0 | `ADMIN` | Open Product Administration. | Active/inactive products appear with category, subtype, currency, status and API version. |
| OPS-009 | P1 | `ADMIN`, empty catalogue | Open Product Administration. | **No products to show** appears. |
| OPS-010 | P0 | `ADMIN` | Open Deposit Operations. | Admin portfolio calls omit customer filter; account and fixed-deposit tables match service data. |
| OPS-011 | P1 | `ADMIN`, empty portfolio | Open Deposit Operations. | Separate **No deposit accounts to show** and **No fixed deposits to show** states appear. |
| OPS-012 | P0 | `ADMIN` | Open Cards & Billing Operations. | Generated bills render across customers as permitted; a truthful warning explains that card-account portfolio search is unavailable. |
| OPS-013 | P1 | `ADMIN`, no bills | Open Cards & Billing Operations. | **No bills to show** appears. |
| OPS-014 | P1 | `ADMIN` | Open Operations Payments. | Backend-contract-required state explains that Payments lacks an admin portfolio search; no customer ID is invented. |
| OPS-015 | P0 | `ADMIN` | Open Journal Search. | Journal, external reference, source, event, date, status, debit and credit match Accounting. |
| OPS-016 | P1 | `ADMIN`, empty journals | Open Journal Search. | **No journals found** appears. |
| OPS-017 | P0 | `ADMIN` | Open GL Accounts, Accounting Rules and Subledger Mappings; use cross-navigation between Rules and Mappings. | Each collection renders dynamic scalar columns correctly; cross-navigation works and active navigation remains Accounting. |
| OPS-018 | P1 | `ADMIN`, empty accounting configuration | Open each Accounting collection. | A collection-specific empty state appears; no blank table is shown. |
| OPS-019 | P1 | `ADMIN` | Open Reconciliation & EOD. | Missing-service/backend-contract state appears; the UI does not fabricate a close status or control totals. |
| OPS-020 | P1 | `ADMIN` | Open Access Administration. | Page accurately explains that create/get exist but list, search and role-lifecycle contracts are missing; no fake user list appears. |
| OPS-021 | P0 | Consumer | Call Accounting/admin portfolio endpoints directly with a consumer token. | Gateway/service returns 403; no Accounting or cross-customer portfolio data is returned. |

## 14. API, resilience and state cases

| ID | Pri | Scenario and steps | Expected result |
|---|---|---|---|
| API-001 | P0 | Inspect a normal GET request. | Request uses Gateway, bearer token, signed tenant, `Accept: application/json` and a UUID correlation ID. |
| API-002 | P0 | Inspect POST/PATCH/PUT/DELETE requests. | JSON content type and a unique idempotency key are sent; retries of one logical command retain backend idempotency semantics. |
| API-003 | P0 | Inspect multipart KYC upload. | Browser-generated multipart boundary is preserved; bearer, tenant, correlation and idempotency headers remain present. |
| API-004 | P1 | Return a structured API problem with correlation ID. | Error title/detail and support reference appear; raw stack traces and secrets do not. |
| API-005 | P1 | Return non-JSON 400/500/502/503 responses. | Stable fallback error appears and page remains navigable. |
| API-006 | P1 | Delay every list/detail response. | Loading state is visible and announced; previous route data is not shown as current data. |
| API-007 | P1 | Navigate away before a slow response completes. | Obsolete request is aborted/ignored; late data does not overwrite the new page. |
| API-008 | P1 | Return `[]`, `{content:[]}` and an empty detail object from appropriate APIs. | Each page displays its designed empty state, never a blank panel/table. |
| API-009 | P1 | Stop one overview dependency. | Dashboard uses partial results and reports degradation; independent services remain usable. |
| API-010 | P1 | Stop the target service for a normal page and select **Try again** after restoring it. | Error state appears, retry makes a fresh request, and recovered data/empty state replaces the error. |
| API-011 | P1 | Return malformed/null optional scalar values and dates. | Page uses a safe placeholder and does not crash the entire application. |
| API-012 | P0 | Cause a mutation to fail after a downstream reservation/hold. | UI does not claim success; backend compensation is reflected by the final payment/account/card state. |
| API-013 | P0 | Inspect browser network during all flows. | No browser request targets internal `/internal/**` endpoints or a business-service port directly. |

## 15. Accessibility, responsive and presentation cases

| ID | Pri | Scenario and steps | Expected result |
|---|---|---|---|
| UI-001 | P1 | Navigate landing, login, shell, search, forms, dialogs and tables with keyboard only. | Logical focus order, visible focus, usable controls and no keyboard trap. |
| UI-002 | P1 | Inspect headings, landmarks, labels, table headers and status/error announcements. | Semantic structure is present; fields have accessible names; errors/loading use appropriate live/alert semantics. |
| UI-003 | P1 | Test at 320, 375, 768, 1280 and 1920 CSS pixels. | No horizontal page overflow; sidebar/mobile navigation and cards/tables remain usable. |
| UI-004 | P1 | Zoom to 200%. | Content remains readable and operable without clipped actions. |
| UI-005 | P1 | Enable reduced motion. | Non-essential transitions/scroll animations are removed. |
| UI-006 | P1 | Check colour contrast and status badges. | Text/actions meet WCAG AA contrast; status is not conveyed by colour alone. |
| UI-007 | P2 | Test long names, references, IDs, amounts and translated-length text. | Layout wraps safely; identifiers remain readable; no control overlaps. |
| UI-008 | P1 | Test INR and another supported currency, zero, negative accounting amount and large amounts. | Formatting is locale-appropriate and never produces `NaN` or an incorrect currency. |
| UI-009 | P1 | Test invalid/missing dates and time-zone boundaries. | No `Invalid Date` or page crash; displayed business dates do not shift incorrectly. |
| UI-010 | P2 | Run browser console inspection while walking all routes. | No uncaught exception, rejected promise, duplicate-key warning or missing-resource error. |

## 16. Route-to-test traceability

| Route | Primary cases |
|---|---|
| `/` | AUTH-001–003, AUTH-007, NAV-005 |
| `/app/overview` | CON-001–003, API-006–010 |
| `/app/profile` | CON-004–007 |
| `/app/kyc` | KYC-001–005 |
| `/app/products` | PROD-001–003 |
| `/app/accounts` | ACC-001–002 |
| `/app/accounts/open` | ACC-005–008 |
| `/app/accounts/{accountId}` | ACC-003–004, ACC-009 |
| `/app/fixed-deposits` | FD-001–002 |
| `/app/fixed-deposits/open` | FD-003–007 |
| `/app/cards` | CARD-001–002, BILL-001 |
| `/app/cards/apply` | CARD-003–005 |
| `/app/bills` | BILL-001–002, BILL-006 |
| `/app/bills/{billId}/pay` | BILL-003–006 |
| `/app/payments` | PAY-001–002 |
| `/app/payments/transfer` | PAY-003–008, PAY-011 |
| `/app/notifications` | NOTIF-001–003 |
| `/app/statements` | STAT-001 |
| `/ops/overview` | OPS-001–002 |
| `/ops/kyc` | OPS-003–007 |
| `/ops/products` | OPS-008–009 |
| `/ops/deposits` | OPS-010–011 |
| `/ops/cards` | OPS-012–013 |
| `/ops/payments` | OPS-014 |
| `/ops/accounting/journals` | OPS-015–016 |
| `/ops/accounting/gl` | OPS-017–018 |
| `/ops/accounting/rules` | OPS-017–018 |
| `/ops/accounting/mappings` | OPS-017–018 |
| `/ops/eod` | OPS-019 |
| `/ops/access` | OPS-020 |

## 17. Suggested execution order

1. Run build/type checks and service health checks.
2. Execute AUTH and role-boundary P0 cases.
3. Execute read-only consumer and operations cases with `C-EMPTY`, then `C-ACTIVE`.
4. Execute mutations in dependency order: profile → KYC → product selection → account → fixed deposit/card → payment/bill.
5. Execute Accounting and notification verification after each money movement.
6. Execute failure, timeout, duplicate-submit and ownership-negative cases.
7. Execute responsive, keyboard, accessibility and console checks.

## 18. Evidence to capture

For every failed case capture the test-case ID, role, route, timestamp, browser, request correlation ID, relevant service logs, expected/actual result and screenshot. For money movement also capture payment ID, idempotency key, reservation/hold ID, journal number, bill status and final balances. Never capture access tokens, refresh tokens, passwords, full PAN/Aadhaar or unmasked card numbers.
