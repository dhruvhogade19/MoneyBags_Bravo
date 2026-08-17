# MoneyBags Oracle JET Frontend Implementation Plan

## 1. Purpose and planning baseline

This document is the implementation blueprint for the MoneyBags browser application. It is based on the controllers, DTOs, security configuration, gateway routes, and service boundaries currently present in this repository on 16 August 2026. It plans the frontend; it does not create the frontend application yet.

The product is a small banking platform with two principal experiences:

- **Customer banking:** customer onboarding and KYC, product discovery, deposit accounts, fixed deposits, credit cards, payments, bills, notifications, and eventually statements.
- **Bank operations:** user provisioning, KYC review, product configuration, customer/account operations, payment investigation, credit-card processing, accounting controls, reconciliation, and eventually EOD orchestration.

The frontend must use public, authorized Gateway APIs. It must never call `/internal/**` endpoints from the browser or access another service's database.

### Delivery status used in this plan

| Status | Meaning |
|---|---|
| **Ready** | A suitable public Gateway endpoint exists now. |
| **Partial** | Some data/actions exist, but a required query, aggregate, authorization rule, or public admin wrapper is missing. |
| **Blocked** | The owning service or required public API does not exist. Design the route and contract, but do not claim the page is functional. |
| **UI-only** | No server persistence is required; the page is shell, help, or local presentation behavior. |

## 2. Recommended application shape

### 2.1 One codebase, two role-aware workspaces

Create one Oracle JET TypeScript virtual-DOM application named `moneybags-web`. Use one authenticated shell and expose two route groups according to signed JWT roles and scopes:

- `/app/*` for `CONSUMER`
- `/ops/*` for `BANK_ADMIN`

This gives one component system, one API layer, one authentication implementation, and one deployment pipeline. A user who has both role sets can switch workspace from the user menu. Do not rely only on hidden navigation: every route and action also requires a scope guard.

The Identity service already defines two public OIDC clients (`moneybags-consumer` and `moneybags-admin`). For the first delivery, host the app at one origin such as `http://127.0.0.1:8000` and register both callback paths on that origin. If separate dev ports `8000` and `8001` are retained, Gateway CORS must explicitly allow both; it currently defaults to only `http://localhost:8000`.

### 2.2 OJET foundation

Use the approved **Warm Editorial Banking** design system defined in [`OJET_UI_Theme_Specification.md`](OJET_UI_Theme_Specification.md) for color, typography, spacing, application chrome, component states, responsive behavior, and accessibility.

Use:

- Oracle JET virtual DOM with TypeScript and Preact.
- Redwood styling and Core Pack (`oj-c-*`) components when the required feature exists; use a legacy JET component only where Core Pack is not yet feature-complete.
- Route-level lazy loading so customer and operations bundles do not load together.
- JET `DataProvider` adapters for tables and list views.
- Mobile-first responsive layouts. Customer journeys must work on phone widths; dense accounting and reconciliation grids may use a desktop-optimized presentation with responsive detail drawers.
- WCAG 2.1 AA as the minimum acceptance target, including keyboard operation, visible focus, landmarks, field labels, error summaries, and non-color status communication.

Oracle's current guidance supports both MVVM and virtual DOM applications, identifies Core Pack as the future-facing Preact component set, and documents DataProvider-backed tables and built-in accessibility support. Pin the actual Oracle JET version during project scaffolding and record it in the frontend README rather than using an unbounded dependency range.

### 2.3 Global application shell

The authenticated shell contains:

- Skip link and WAI-ARIA `banner`, `navigation`, `main`, and `contentinfo` landmarks.
- Top bar: MoneyBags mark, current workspace, environment badge outside production, global notification indicator, help, and user menu.
- Responsive navigation: persistent side navigation on large screens and modal drawer on small screens.
- Breadcrumb and page header with title, description, status, primary action, and optional overflow actions.
- Global message region for outages, degraded services, session expiry, and important completion messages.
- Correlation-ID-aware error details with a copy button.

Customer navigation:

1. Overview
2. Products
3. Accounts
4. Fixed Deposits
5. Cards
6. Payments
7. Bills
8. Statements
9. Notifications
10. Profile & KYC

Operations navigation:

1. Operations Overview
2. Customers & KYC
3. Products
4. Deposits & Fixed Deposits
5. Cards & Billing
6. Payments
7. Accounting
8. Reconciliation & EOD
9. Notifications
10. Access Administration

## 3. Authentication, authorization, and request rules

### 3.1 Sign-in flow

Use OAuth 2.0 Authorization Code with PKCE. The application redirects to the Identity service, validates `state` and `nonce`, exchanges the code, loads claims, and routes by role. Required claims are `roles`, `user_id`, `tenant_id`, and optional `customer_id`; audience must be `moneybags-api`.

- Keep access tokens in memory, not `localStorage`.
- Use the rotating refresh token to restore the session during the eight-hour refresh lifetime.
- Access token lifetime is ten minutes, so refresh before expiry and queue concurrent requests behind a single refresh operation.
- On failed refresh, clear sensitive state and redirect to sign-in with a return URL.
- Customer-bound forms take the customer/CIF identifier from the signed session wherever the backend permits it. Never let a consumer edit another customer's ID in a request.

### 3.2 Gateway request policy

Every API request goes to the Gateway base URL and includes:

- `Authorization: Bearer <access-token>`
- `X-Tenant-ID`: exact signed `tenant_id`
- `X-Correlation-ID`: a new UUID per logical interaction; reuse it for retries of the same interaction
- `Idempotency-Key`: a stable UUID for every supported mutation; retain it until a definitive response or successful lookup

Retry only safe reads automatically. Do not blindly retry POST/PATCH/DELETE. Disable the submit action after the first click, show an in-progress state, and resolve uncertain results using the resource lookup endpoint where one exists.

### 3.3 Scope groups

Customer routes use the existing read/write scopes for products, CIF, KYC, accounts, fixed deposits, cards, payments, and notifications. Operations routes use the corresponding `*:admin` or review scopes. Components receive an `AllowedAction` model so buttons, table actions, routes, and API calls are governed consistently.

## 4. Common and unauthenticated pages

### C01. Welcome and sign in — `/`

- **Purpose:** explain the two workspaces and start the correct OIDC login.
- **Content:** short product introduction, Customer Banking and Bank Operations sign-in cards, environment/support information.
- **Actions:** sign in as customer; sign in as bank staff.
- **States:** Identity unavailable, callback misconfiguration, already authenticated redirect.
- **Backend:** Identity authorization endpoint. **Status: Ready**, subject to final redirect/CORS configuration.

### C02. Authentication callback — `/auth/callback/:client`

- **Purpose:** complete PKCE, validate the returned authorization response, build the session, and redirect to the saved route.
- **Content:** progress indicator only; never render tokens or authorization codes.
- **Failure handling:** invalid state/nonce, denied consent, expired code, token exchange failure, missing role, invalid audience.
- **Backend:** Identity token and user-info/JWT claims. **Status: Ready**.

### C03. Access denied — `/forbidden`

- Shows the missing permission in user-friendly wording, the attempted area, and navigation back to the permitted workspace.
- Never reveal server internals or offer an action the user cannot perform. **Status: UI-only**.

### C04. Session expired — `/session-expired`

- Explains that unsaved data may be lost, offers sign-in, and preserves only a safe return route.
- Clear API caches and all customer financial data on entry. **Status: UI-only**.

### C05. Not found and service unavailable — `/not-found`, `/unavailable`

- Not-found provides route recovery. Unavailable presents retry, service health context appropriate for the user, and correlation ID.
- Never expose Eureka or actuator endpoints to the browser. **Status: UI-only**.

## 5. Customer banking pages

### U01. Customer overview — `/app/overview`

- **Purpose:** one actionable view of the customer's banking position.
- **Widgets:** profile/KYC completion, deposit balances, fixed-deposit principal and next maturity, total card outstanding and available credit, next bill due, recent payments, unread/recent notifications, shortcuts.
- **Actions:** complete KYC, open account, book FD, apply for card, transfer money, pay card bill.
- **APIs:** CIF detail; KYC by CIF; deposit and FD lists; credit-card accounts by CIF; payments by customer; notifications by CIF.
- **Data rule:** load widgets independently so one failed service does not blank the dashboard. Show an `as of` value and stale indicator for balances.
- **Status: Partial**. The dashboard is implementable, but “next bill due” needs a public bill search/summary endpoint.

### U02. Profile onboarding wizard — `/app/onboarding`

- **Purpose:** create the CIF linked to the authenticated identity.
- **Steps:** personal details; contact/address; employment/income; PAN and Aadhaar; review/consent; success.
- **Fields:** first/last name, date of birth, calculated age, email, 10–15 digit mobile, address, employment type, optional salary, PAN, Aadhaar.
- **Rules:** derive age from DOB in the UI but send the value required by the current DTO; uppercase PAN; digits-only Aadhaar/mobile; mask sensitive identifiers on review and after creation.
- **API:** `POST /api/v1/cifs`.
- **States:** existing linked CIF, field conflict, identity-link failure after CIF creation, safe retry with idempotency key if supported.
- **Status: Ready**, although the backend should eventually remove duplicated client-supplied age.

### U03. Profile details and edit — `/app/profile`

- **Layout:** identity summary, contact details, employment details, KYC badge, protected identifiers, creation/update timestamps.
- **Actions:** edit allowable profile fields; go to KYC; copy CIF ID.
- **APIs:** `GET/PUT /api/v1/cifs/{cifId}` and customer-contact detail endpoint.
- **Security:** only masked PAN/Aadhaar by default; reveal must be separately authorized and audited if introduced.
- **Status: Ready**.

### U04. KYC status — `/app/kyc`

- **Content:** current status/decision, initiation and review timeline, document checklist, rejection or mismatch explanation, CIF/notification sync status.
- **Actions:** start KYC from existing CIF data; upload/replace permitted documents; download own document; retry a failed synchronization only if the customer's authorization is explicitly retained by the backend.
- **APIs:** create KYC, KYC by CIF/ID, list/download documents.
- **States:** not started, documents required, under review, more information/mismatch, approved, rejected, downstream sync failed.
- **Status: Ready** for core flow; confirm that `/sync` remains suitable for consumer exposure before showing that action.

### U05. KYC document upload — `/app/kyc/:kycId/documents/new`

- **Fields:** document type, file, document metadata required by the controller.
- **UX:** file picker with permitted extension/size text, client validation, upload progress, cancel before transmission, success receipt.
- **Security:** do not preview unsafe types inline; use server media type and attachment disposition; never persist file bytes in browser storage.
- **API:** multipart `POST /api/v1/kycs/{kycId}/documents`.
- **Status: Ready**. Exact accepted document types and size limits must be exported as configuration or documented contract before implementation.

### U06. Product catalogue — `/app/products`

- **Content:** active Savings, Current, Fixed Deposit, and Credit Card products grouped by category; search and lightweight filters.
- **Card data:** product name/code, subtype, headline rate or card interest, minimum opening amount/limit, tenure range, key fees, eligibility headline, features.
- **Actions:** compare up to three, view details, start relevant application.
- **APIs:** active and category-active Product Master endpoints; minimal credit-card endpoint.
- **Status: Ready**.

### U07. Deposit product detail — `/app/products/deposits/:productCode`

- **Sections:** overview, eligibility, balances/amount rules, interest calculation and posting, rate slabs, fees, features, closure terms.
- **Actions:** calculate rate/return; check eligibility; open account or start FD quote.
- **APIs:** product detail, eligibility, pricing/rate quote, account-opening validation.
- **Status: Ready**.

### U08. Credit-card product detail — `/app/products/cards/:productCode`

- **Sections:** interest rate, limit range, interest-free days, minimum payment, due-day rule, cash advance terms, fees, eligibility, features.
- **Actions:** eligibility check using signed customer data; apply for card.
- **APIs:** product detail/minimal detail and credit-card application validation.
- **Status: Ready**.

### U09. Product comparison — `/app/products/compare`

- Compares only like categories. Rows include rate, opening/limit range, tenure, fees, eligibility, closure or payment rules, and features.
- Data comes from already fetched product details; selected product codes may be stored in the URL, not sensitive storage. **Status: Ready**.

### U10. Deposit accounts list — `/app/accounts`

- **Content:** account card/table toggle with masked number, product, status, available balance, currency, branch, and balance timestamp.
- **Filters:** status; product client-side within the returned customer set; paging.
- **Actions:** view, transfer, manage, close, open another account.
- **API:** `GET /api/deposit-accounts?customerId=...&status=...&page=...&size=...`.
- **Status: Ready**.

### U11. Open deposit account wizard — `/app/accounts/open/:productCode?`

- **Steps:** product; ownership; opening amount/currency; branch and operating instruction; nominees; review; eligibility result; submission; receipt.
- **Fields:** customer IDs and primary customer, product/version, currency, opening amount, servicing branch, operating instruction, nominees, channel, external reference.
- **Rules:** prefill signed customer; joint holders require a real customer lookup/verification process; nominee allocation total must equal 100%; preserve the exact product version evaluated.
- **APIs:** eligibility check then `POST /api/deposit-accounts`.
- **Status: Partial**. Single-holder opening is ready. Joint account UX needs a safe public CIF lookup/invitation contract; do not accept arbitrary customer IDs as a production design.

### U12. Deposit account detail — `/app/accounts/:accountId`

- **Tabs:** Overview, Parties, Limits & Mandates, Status History, Closure.
- **Overview:** masked number, status, product/version, currency, branch, operating instruction, ledger/available/blocked balance, timestamps.
- **Actions:** transfer, refresh balance, manage account, request closure.
- **APIs:** account detail, balance, status history.
- **Status: Ready**.

### U13. Account parties — `/app/accounts/:accountId/parties`

- **Content:** holders with role/authorization/ownership; nominees with relationship/allocation/status.
- **Actions:** add/remove holder, replace nominee allocation.
- **Safety:** destructive actions use a confirmation dialog summarizing effect; concurrency/version conflict refreshes the record instead of overwriting.
- **APIs:** holder POST/DELETE and nominee PUT.
- **Status: Ready** for authorized operations; normal customer permissions for holder changes should be product-approved before exposure.

### U14. Limits and mandates — `/app/accounts/:accountId/controls`

- **Limits:** type, amount, currency, effective dates; edit by limit type.
- **Mandates:** authorized customer, type, validity, status; add/revoke.
- **Account command panel:** only commands permitted for the current state, with reason code/text and effective time.
- **APIs:** limit PUT, mandate POST/DELETE, account command POST.
- **Status: Ready** technically. Separate consumer-safe commands from bank-admin commands in backend authorization before enabling all controls.

### U15. Account closure — `/app/accounts/:accountId/closure`

- **Flow:** enter destination/reason as applicable; request quote; show balance, fee/settlement, blockers, and quote expiry; confirm; show request tracker; allow cancel when eligible.
- **APIs:** closure quote, create/list/get/cancel closure requests.
- **States:** eligible, blocked by balance/mandate/reservation/bill/accounting clearance, processing, completed, failed, cancelled.
- **Status: Ready**, but canonical references must be consistent across owners for reliable financial clearance.

### U16. Fixed deposits list — `/app/fixed-deposits`

- **Content:** masked number, product, principal, rate, value/maturity dates, expected maturity amount, status, funding/payout accounts.
- **Filters:** status, maturity period; paging if supported.
- **Actions:** detail, book FD, premature closure for eligible states.
- **API:** FD list by signed customer and optional status.
- **Status: Ready**.

### U17. Fixed-deposit quote and booking — `/app/fixed-deposits/open`

- **Steps:** product; principal/tenure/payout frequency/value date; quote; source and payout accounts; holders/nominees; review; book/fund; receipt.
- **Quote result:** slab, annual rate, maturity date, projected interest/maturity amount, calculation/compounding/day-count basis.
- **Booking fields:** customer/primary customer, locked product version, principal/currency/tenure, payout frequency, funding/payout accounts, branch, nominees, channel, reference.
- **APIs:** FD quote and booking. Funding is orchestrated as part of booking/payment integration; the UI tracks resulting states rather than invoking internal callbacks.
- **Status: Ready** for single customer. Joint ownership has the same lookup dependency as U11.

### U18. Fixed-deposit detail — `/app/fixed-deposits/:fdId`

- **Tabs:** Overview, Projected Schedule, Accrual History, Premature Closure.
- **Content:** principal/rate, accrued interest, value/maturity dates, expected totals, linked accounts, status/version.
- **APIs:** FD detail, projected schedule, accrual list.
- **Status: Ready**.

### U19. FD premature closure — `/app/fixed-deposits/:fdId/close`

- **Flow:** select destination; request quote; show earned interest, penalties, payout, blockers and quote expiry; confirm; track request.
- **APIs:** premature-closure quote, request, and request lookup.
- **Status: Ready**. The UI must make the irreversible financial impact explicit before confirmation.

### U20. Cards overview — `/app/cards`

- **Sections:** card accounts and application statuses. Card tiles show masked PAN only, product, status, available/sanctioned limit, outstanding, and rate.
- **Actions:** apply, view application, view card, pay bill, close card.
- **APIs:** accounts by CIF and applications by CIF.
- **Status: Ready**.

### U21. Credit-card application wizard — `/app/cards/apply/:productCode?`

- **Steps:** product; requested limit; eligibility preview; declarations; review; submit; application receipt.
- **Fields:** CIF from token/session, product code, requested credit limit. Age, income, and KYC shown read-only from CIF/KYC.
- **APIs:** Product Master validation then `POST /api/credit-cards/applications`.
- **States:** ineligible with reasons, submitted/pending, duplicate/in-progress.
- **Status: Ready**.

### U22. Card application detail — `/app/cards/applications/:applicationId`

- **Content:** product, requested/approved limit, rate snapshot, KYC/age/salary snapshots, eligibility and application statuses, submitted/updated timeline.
- **Actions:** return to products; view created account when available.
- **API:** application by ID. **Status: Ready**.

### U23. Credit-card account detail — `/app/cards/:accountId`

- **Content:** masked card number, status, product, sanctioned and available limits, outstanding, rate, opened date, latest bill summary when available.
- **Actions:** pay bill, view bills, request close. Do not expose holds/capture/release—they are Payments-owned server-to-server operations.
- **APIs:** account detail, available limit, interest rate, bill data.
- **Status: Partial** because bill summary/search is not public.

### U24. Close credit-card account — `/app/cards/:accountId/close`

- **Flow:** show zero-outstanding and accounting/billing clearance requirements; reason and confirmation; submit; track resulting account state.
- **API:** card close endpoint.
- **Status: Ready** at API level, but closure correctness depends on all services adopting canonical `CC-<id>` references.

### U25. Payments list — `/app/payments`

- **Content:** type, counterparty/destination, amount/currency, status, reference, business date, created/settled time.
- **Filters:** page; type/status/date client-side only if not supported server-side. Avoid implying global filtering over unloaded pages.
- **Actions:** transfer, open detail, cancel when allowed.
- **API:** payments by signed customer with paging.
- **Status: Ready**.

### U26. Book transfer — `/app/payments/transfer`

- **Steps:** source account; target account; amount; reference; review; submit; receipt.
- **Fields:** customer from session, source/target account IDs, amount, currency, optional reference.
- **Rules:** source and target differ, currency alignment, positive amount, available-balance hint is advisory because the server is authoritative.
- **API:** `POST /api/v1/payments/book-transfers`, then payment lookup for uncertain/time-out outcomes.
- **Status: Ready**.

### U27. Merchant card payment — `/app/payments/card-purchase`

- **Purpose:** demonstration/test journey for the currently exposed merchant-payment API. In a real production card system, merchant authorization normally originates outside customer online banking.
- **Fields:** card account, merchant ID, amount/currency, reference.
- **API:** credit-card merchant payment; Payments sends Accounting `merchantId` and uses canonical `CC-<id>`.
- **Status: Ready**, but place under a “Demo transactions” feature flag rather than primary customer navigation.

### U28. Card bill repayment — `/app/bills/:billId/pay`

- **Fields:** funding deposit account, bill/card read-only context, amount defaulted to outstanding, currency, reference.
- **Review:** distinguish minimum, statement outstanding, and entered amount; show source balance; prevent more than the supported amount if backend rules require it.
- **API:** Payments credit-card repayment endpoint, then payment lookup.
- **Status: Partial** because starting from a known bill is supported, while bill discovery is not.

### U29. Payment detail and cancellation — `/app/payments/:paymentId`

- **Content:** status timeline, type, instruments/counterparty, principal/interest where relevant, amount, references, journal/reversal reference, business date and timestamps.
- **Actions:** cancel only while backend status permits; retry display lookup, never resubmit the original payment with a new idempotency key after uncertainty.
- **Failure display:** translate failure code/message into user wording; keep correlation ID in expandable support details.
- **APIs:** payment lookup and cancel.
- **Status: Ready**. The backend compensation gap for some repayment failures must be closed before calling the flow production-safe.

### U30. Bills list — `/app/bills`

- **Content:** billing period, total/minimum/paid/outstanding amounts, due date, and status; filters for card and status.
- **Actions:** view bill, pay outstanding.
- **Required API:** customer-authorized `GET /api/v1/bills?cifId=...` or `?accountId=...` with paging and summary rows.
- **Status: Blocked**. Current bill search is internal and Gateway-blocked.

### U31. Bill detail — `/app/bills/:billId`

- **Content:** summary amounts, due date/status, card account reference, line items with type/source/description/amount/time.
- **Actions:** pay when outstanding is positive; open resulting payment.
- **API:** `GET /api/v1/bills/{billId}`.
- **Status: Ready** only when the user reaches a known, authorized bill ID. Verify bill ownership enforcement before release.

### U32. Statements — `/app/statements`

- **Pages contained:** statement list; request/generate statement; statement detail; PDF/CSV download.
- **Filters:** account/card, statement period, status. Detail groups opening balance, activities, fees/interest, closing balance, and generation metadata.
- **Status: Blocked**. There is no Statement Service module; only design/OpenAPI material exists. Do not assemble an authoritative statement in the browser from Payments alone.

### U33. Notifications — `/app/notifications`

- **Content:** paged list by signed CIF, type, subject, status, created/sent time; detail drawer renders email body as escaped text.
- **Actions:** open related resource based on a safe source-reference mapping.
- **API:** notification list by CIF and detail by ID.
- **Status: Ready**. There is no read/unread mutation, so label the UI “recent notifications,” not an inbox with persisted unread state.

## 6. Bank operations pages

### O01. Operations overview — `/ops/overview`

- **Widgets:** KYC queue counts, pending card applications, payment exceptions, billing exceptions, reconciliation blockers, EOD/business-date state, recent operational notifications.
- **Behavior:** each widget loads independently and links to its owning work queue.
- **Status: Partial**. KYC and accounting lookups are usable; broad card/payment/bill/notification queue endpoints and EOD orchestration are missing.

### O02. Customer search and detail — `/ops/customers`, `/ops/customers/:cifId`

- **Search fields:** CIF, name, email, mobile, PAN suffix, KYC status; results must mask identifiers.
- **Detail tabs:** profile, KYC, deposit accounts/FDs, cards, payments, bills, notifications; links open owning-service pages rather than merging records for mutation.
- **Status: Blocked** for general search. CIF supports lookup by known ID but no paged/search endpoint. Build the detail route after adding an admin-scoped search contract.

### O03. KYC work queue — `/ops/kyc`

- **Columns:** KYC/CIF, customer, status/decision, initiated time, document count, sync states, assigned/reviewed by.
- **Filters:** CIF, status/decision and paging supported by the actual endpoint; show only server-supported filters.
- **Actions:** open case; retry failed sync when authorized.
- **API:** `/api/v1/kycs/admin/work-queue`.
- **Status: Ready**.

### O04. KYC case review — `/ops/kyc/:kycId`

- **Layout:** customer snapshot, submitted data versus CIF values, document list/view/download, review timeline, mismatch/rejection details, downstream sync panel.
- **Actions:** verify/reject each document with reason; approve/reject KYC with required reason; retry CIF sync.
- **APIs:** KYC detail/documents/download, document verification, decision, sync.
- **Status: Ready**. Confirm method security on review endpoints consistently requires `kyc:review`; route security alone is not sufficient.

### O05. Product catalogue administration — `/ops/products`

- **Columns:** code, name, category/subtype, currency, lifecycle status, effective period, version, updated by/time.
- **Filters:** category, status, effective date, code/name; paging.
- **Actions:** create, clone, edit, change status, retire/delete only under allowed lifecycle rules.
- **APIs:** product list/detail/create/update/status/delete.
- **Status: Ready**.

### O06. Product editor — `/ops/products/new`, `/ops/products/:productCode/edit`

- **Sections:** identity/effective dates; amount rules; interest and benchmark pricing; FD rules/slabs/renewal/premature closure; card rules; fees; eligibility; features; account closure.
- **Dynamic form:** show only sections applicable to category/subtype. Use repeatable editable tables for slabs, fees, eligibility, and features.
- **Validation:** date ranges, non-overlapping slabs, min ≤ max, percentages 0–100, currency format, required policy versions, category consistency.
- **Review:** semantic diff before save, including version and changed-by actor from session.
- **Status: Ready**, but editing should use optimistic conflict handling and immutable published versions where business policy requires it.

### O07. Benchmark rates — `/ops/benchmarks`

- **Content:** benchmark code selector, effective rate, as-of date, history timeline/table.
- **Actions:** add effective-dated rate; inspect affected floating-rate products.
- **APIs:** benchmark create/effective/history.
- **Status: Ready**.

### O08. Deposit and FD operations — `/ops/deposits`

- **Search:** customer ID and status are supported; account ID can open detail directly. Provide separate Deposit and Fixed Deposit tabs.
- **Detail/actions:** reuse U12–U19 components with admin action policy; keep sensitive commands in an operations action panel with reason/confirmation.
- **Status: Partial**. Known-customer/status search works; bank-wide product/date/exception queues need admin search endpoints.

### O09. Credit-card application queue — `/ops/cards/applications`

- **Columns:** application, CIF/customer, product, requested limit, eligibility, KYC snapshot, submitted time, status.
- **Actions:** review, approve with approved limit as allowed, reject with reason, create account after approval.
- **Status: Blocked** for the queue because only by-ID and by-CIF reads exist. Add an admin-scoped paged application search. The approve/reject/account-create actions themselves exist.

### O10. Credit-card application review — `/ops/cards/applications/:applicationId`

- **Content:** application and eligibility snapshots, live CIF/KYC links, product policy snapshot, requested vs proposed approved limit, decision timeline.
- **Actions:** approve, reject, create account. Each is idempotent, confirmed, and state-dependent.
- **Status: Partial**. Known-ID flow is implementable; backend DTOs should confirm rejection reason and approval-limit inputs because current request signatures may not capture an auditable rationale.

### O11. Card account operations — `/ops/cards/accounts/:accountId`

- Reuse card detail plus lifecycle/clearance panel and safe close action. Holds are observable only through server-owned operational data if a future admin query is added; never call hold/capture/release from the browser.
- **Status: Partial**. Known account is supported; bank-wide account search and operational history are missing.

### O12. Billing operations — `/ops/billing`

- **Pages:** bill search/list; bill detail; generation request; close-cycle status; settlement investigation.
- **Required behavior:** filter by account, period, status, due date; generate/retry with idempotency; display line sources and accounting/payment references.
- **Status: Blocked** as a browser workspace. Generation, search, settlement, and close endpoints are under `/internal/**` and correctly blocked by Gateway. Add purpose-built `billing:admin` public controller methods rather than exposing internal endpoints.

### O13. Payment operations search — `/ops/payments`

- **Columns:** payment ID, customer, type, source/destination/merchant, amount, status, business date, timestamps, journal, failure.
- **Filters:** ID, customer, status, type, date range, account/reference, correlation ID.
- **Actions:** inspect; cancel/compensate only through a deliberately designed admin command with reason and audit.
- **Status: Partial**. Customer-bound listing and known-ID lookup exist; broad internal filtering/reversal endpoints cannot be exposed directly. Add a public `payment:admin` search and command facade.

### O14. Payment investigation detail — `/ops/payments/:paymentId`

- **Tabs:** overview, orchestration timeline, financial references, failure/compensation, audit metadata.
- **Content:** reservation/hold IDs, Accounting journal/reversal, bill/FD references, correlation ID and timestamps. Do not display raw credentials or internal request bodies.
- **Status: Ready** for the public PaymentResponse fields; deeper attempt history requires a new sanitized admin endpoint.

### O15. Journal search — `/ops/accounting/journals`

- **Filters:** business date, source service, event type, external reference, page/size.
- **Columns:** journal number, posting sequence, external reference, source/event, date, currency, debit/credit, status, reversal link.
- **Actions:** open journal, copy reference/correlation ID. Journal reversal remains server-owned unless a reviewed public admin command is added.
- **API:** public journal search. **Status: Ready**.

### O16. Journal detail — `/ops/accounting/journals/:journalNumber`

- **Header:** status, totals/balance check, source/event, external and correlation references, business/occurred/posted times, reversed journal link.
- **Lines:** number, GL, subledger, component, rule/version, debit, credit, narration; totals pinned in footer.
- **Status: Ready**.

### O17. GL account administration — `/ops/accounting/gl-accounts`

- **List/create/detail:** code, name, type, normal balance, currency, parent, status/version.
- **Actions:** create and change status with impact warning; no destructive delete.
- **APIs:** GL create/list/get/status.
- **Status: Ready**.

### O18. Accounting rules — `/ops/accounting/rules`

- **List/editor fields:** rule/event/component/product/currency/version, debit and credit mappings, effective dates, status.
- **Actions:** create and inspect. Validate referenced mappings and effective periods in the UI but rely on server enforcement.
- **Status: Ready** for current create/list operations; add detail/status/versioning endpoints if in-place maintenance is required.

### O19. Subledger mappings — `/ops/accounting/mappings`

- **Fields:** mapping code, optional product, GL code, currency, effective dates, status.
- **Actions:** create, filter, inspect linked GL/rules.
- **Status: Ready** for create/list; lifecycle maintenance may need further endpoints.

### O20. Trial balance — `/ops/accounting/trial-balances/:runId`

- **Content:** run/business date/currency/generator/time, balanced badge, debit/credit totals/difference, GL line table.
- **Actions:** export current representation client-side only if compliance permits; link to reconciliation.
- **Status: Ready** for known run ID. Generation is internal/EOD-owned and must not be triggered directly from this page.

### O21. Reconciliation run — `/ops/reconciliation/:runId`

- **Summary:** expected/actual counts and totals, status and business date.
- **Items:** reference, expected/actual/difference, blocking flag, status, resolution, actor/time.
- **Actions:** resolve an item with status, mandatory explanation, and session actor; confirm blocking resolutions.
- **Status: Ready** for known run ID and item resolution. A run list/search endpoint is missing.

### O22. Accounting period — `/ops/accounting/periods/:businessDate`

- **Content:** business date, open/closed status, opened/closed times and actors, version, links to related EOD artifacts.
- **Actions:** read only in the accounting workspace. Open/close belongs to the EOD orchestrator.
- **Status: Ready** for lookup; lifecycle commands remain internal.

### O23. EOD control center — `/ops/eod`

- **Planned content:** current business date; run state and stepper; service readiness; cutoff status; pending blockers; generated bills/accruals/trial balance/reconciliation; rerun/resume/cancel controls; immutable command/audit timeline.
- **Planned flow:** readiness → stop payment intake → close operational services → bill/accrual processing → trial balance → reconciliation → close accounting period → advance business date → reopen intake.
- **Status: Blocked**. No EOD/Reconciliation Orchestrator module exists. Individual service controls are not a safe substitute for an orchestrator, and the browser must not sequence internal endpoints.

### O24. Notification operations — `/ops/notifications`

- **Planned content:** search by CIF, type, source reference, status, and date; delivery detail and retry/dead-letter context.
- **Status: Partial**. Known-CIF list/detail exists; bank-wide search, retry, and outbox/delivery lifecycle are missing.

### O25. Access administration — `/ops/access/users/new`, `/ops/access/users/:userId`

- **Create:** login identity fields accepted by Identity service, role assignment, customer link where applicable, review and receipt.
- **Detail:** user ID, safe profile/role/link state; no credential display.
- **Status: Partial**. Create and known-ID read exist. There is no user list/search, update, disable, reset, or unlock administration contract.

## 7. Shared components and interaction patterns

Build reusable domain components rather than duplicating screens:

| Component | Responsibility |
|---|---|
| `AppShell` | Role-aware navigation, landmarks, header, messages, user menu. |
| `PageHeader` | Breadcrumb, title, description, status, primary/overflow actions. |
| `Money` | Decimal-safe amount formatting with explicit currency; never calculate money with binary floating point. |
| `StatusBadge` | Text + icon + color mapping for lifecycle states; unknown values remain visible. |
| `MaskedIdentifier` | Account/card/PAN/Aadhaar masking and safe copy behavior. |
| `ServerTable` | DataProvider adapter, server paging/filtering/sorting, empty/error/loading states. |
| `DetailSummary` | Responsive label/value presentation with copy and links. |
| `Timeline` | Application, payment, KYC, closure, and EOD state history. |
| `StepWizard` | Validated multi-step application and transaction flows with review screen. |
| `ConfirmFinancialAction` | Amount, source/destination, consequences, consent, idempotent submit. |
| `ProblemBanner` | Normalized error, retry guidance, timestamp and correlation ID. |
| `ServicePanel` | Independently loading dashboard widget with degraded state. |
| `DocumentPicker` | KYC file validation/progress and secure download handling. |
| `PolicyForm` | Effective dating, repeatable policy rows, validation and diff review. |
| `UnsavedChangesGuard` | Prevent accidental route/logout loss without trapping keyboard users. |

Every data page must define five states: initial loading, loaded, no results/not created, recoverable error, and forbidden/not found. Mutations add validating, submitting, uncertain outcome, success receipt, conflict, and definitive failure.

## 8. Frontend technical architecture

### 8.1 Proposed repository layout

```text
moneybags-web/
  package.json
  oraclejetconfig.json
  tsconfig.json
  src/
    index.html
    components/
      app-shell/
      common/
      domain/
    routes/
      common/
      customer/
      operations/
    services/
      auth/
      api/
      telemetry/
    state/
      session/
      preferences/
    contracts/
      generated/
      adapters/
    styles/
      tokens/
      app.scss
    tests/
  e2e/
  README.md
```

The root Maven reactor should not try to compile the Node/OJET project. Add root convenience scripts separately (for example `run-web.ps1`) and optionally make `run-all.ps1` start it after the backend becomes stable.

### 8.2 API and contract layer

- Generate or hand-maintain typed interfaces from each service's public OpenAPI document, then wrap them in domain repositories. Never import a service's Java DTO or infer contracts from entity classes.
- One HTTP client owns auth refresh, Gateway headers, timeout policy, JSON parsing, multipart/download handling, and error normalization.
- Normalize Spring `ProblemDetail` and existing custom error bodies into `{status, code, title, detail, fieldErrors, correlationId}`.
- Keep API objects separate from view models, especially where services use inconsistent page formats or names.
- Use ISO dates in requests. Centralize display timezone and distinguish `LocalDate` business dates from instants/offset timestamps.
- Treat money as decimal strings in the transport/view model and use a decimal library only when the UI must calculate projections; server results remain authoritative.

### 8.3 State and caching

- Session store: claims, tokens, scopes, customer ID, tenant, expiry.
- Server-state cache: keyed by service/resource/filter; short-lived and invalidated after mutations.
- Route state: paging/filtering/tab in the URL so admin investigations are shareable without storing sensitive response data.
- Wizard state: memory/session-only draft with an explicit discard. Do not persist PAN, Aadhaar, documents, tokens, balances, or payment forms in local storage.
- Do not create a single giant client-side “customer object.” Each service remains authoritative for its resources.

### 8.4 Observability and audit support

- Generate a correlation ID for each page load interaction and mutation; surface it on errors and receipts.
- Record frontend route, duration, response status, and sanitized error code. Never log tokens, PAN/Aadhaar, full card/account numbers, KYC files, request bodies containing PII, or financial narration.
- Add an environment-configured telemetry adapter; do not hard-wire a vendor in domain code.

### 8.5 Responsive and accessibility rules

- Customer flows support 320px and larger; convert wide tables to cards or master-detail layouts on small screens.
- Operations tables support keyboard navigation, meaningful row actions, sticky identifiers where available, and an accessible detail alternative.
- All form controls have visible labels and linked help/errors. On submit, focus the error summary then permit navigation to each invalid field.
- Dialogs return focus correctly. Toasts are not the only record of financial success; use durable receipt pages.
- Status is never conveyed by color alone. Charts always have a table/text equivalent.
- Test at 200% and 400% zoom, keyboard-only, high contrast, and a supported screen reader.

## 9. Backend work required for the planned frontend

### P0 — required before trustworthy end-to-end customer flows

1. Finish canonical credit-card account reference adoption (`CC-<id>`) in Credit Card and Bill Generation, including lifecycle, billing, payment, and Accounting subledger use.
2. Verify ownership authorization on `GET /api/v1/bills/{billId}` and every known-ID detail route.
3. Add a customer-authorized, paged bill list/summary endpoint by CIF/account.
4. Close the Credit Card bill-payment idempotency gap and the Payments card-repayment compensation gap.
5. Standardize Gateway CORS and Identity redirect URIs for the chosen single-origin deployment.
6. Document KYC upload types, file-size limits, and malware/content validation contract.

### P1 — required for a usable bank-operations workspace

1. Admin-scoped CIF/customer search.
2. Admin-scoped credit-card application and account searches.
3. Public admin Payments search/investigation facade; do not expose `/internal/**`.
4. Public admin Billing search/generation/operations facade.
5. Reconciliation run list/search.
6. Notification operations search and delivery/retry model.
7. Identity user list/search and lifecycle commands.
8. Consistent page envelope, error envelope, filter naming, and correlation header spelling across services.

### P2 — new application capabilities

1. Implement Statement Service and its customer/admin public contracts.
2. Implement an EOD/Reconciliation Orchestrator with durable run/step state, idempotent commands, recovery, audit, and public operations projections.
3. Replace in-memory Payments cutoff control with durable/shared state.
4. Add a transactional notification outbox and delivery status/retry lifecycle.

## 10. Delivery sequence

### Phase 0. Contract freeze and design system (1 sprint)

- Confirm personas, deployment origin, OIDC callbacks, route map, navigation labels, status vocabulary, and design tokens.
- Export current public OpenAPI contracts and record backend gaps as tracked work.
- Prototype shell, table, form, wizard, receipt, and error patterns in OJET.
- Exit: approved information architecture and P0 contract owners assigned.

### Phase 1. Foundation and onboarding (1–2 sprints)

- Scaffold TypeScript virtual-DOM OJET project, auth/PKCE, Gateway client, route/scope guards, shell, common components, CI checks.
- Deliver C01–C05 and U02–U05.
- Exit: customer signs in, creates/views profile, completes KYC document journey; admin reviews KYC.

### Phase 2. Products, deposits, and FDs (2 sprints)

- Deliver U01 dashboard baseline, U06–U19, O05–O08.
- Exit: customer discovers a product, opens and manages an account, books/views/closes an FD using live APIs.

### Phase 3. Cards, payments, bills, notifications (2 sprints)

- Complete P0 backend items first.
- Deliver U20–U31, U33, O09–O14, O24.
- Exit: application-to-card, transfer, purchase demo, bill repayment, cancellation/investigation, and notification flows have verified receipts and recovery behavior.

### Phase 4. Accounting and access operations (1–2 sprints)

- Deliver O15–O22 and O25 after P1 search/lifecycle APIs land.
- Exit: staff can search journals, administer core Accounting configuration, inspect trial balance/reconciliation, and provision users under least privilege.

### Phase 5. Statements and EOD (after service implementation)

- Deliver U32, statement operations, O23, and cross-service operational summaries.
- Exit: durable EOD can be started, observed, recovered, and audited without the browser directly orchestrating internal service calls.

### Phase 6. Production hardening (continuous, final gate)

- Cross-browser/device and accessibility verification.
- Threat modelling, dependency/SBOM checks, CSP/security headers, token/session tests, and PII log review.
- Load tests for dashboards/tables, slow-network tests, timeout/idempotency/recovery scenarios, and resilience under partial service failure.
- Operational runbook, support correlation-ID workflow, analytics/privacy review, and deployment rollback exercise.

## 11. Test strategy and definition of done

### Test layers

- **Unit:** converters, validators, scope decisions, status mappings, masking, decimal/date formatting.
- **Component:** forms, server tables, error/empty states, wizards, confirmations, focus behavior.
- **Contract:** generated types and mocked responses validated against each public OpenAPI document; fail CI on incompatible drift.
- **Integration:** OIDC callback/refresh/logout, Gateway headers, paging, multipart upload, downloads, uncertain mutation recovery.
- **E2E:** separate customer and admin personas against an isolated Oracle-backed environment.
- **Accessibility:** automated checks plus keyboard and screen-reader manual scenarios.

### Critical E2E journeys

1. Sign in → create CIF → initiate KYC → upload documents → admin approves → customer sees approved status.
2. Browse deposit product → eligibility → open account → view balance/history → request closure.
3. Quote/book FD → fund → view schedule/accrual → premature-close quote/request.
4. Apply for card → admin approves/creates account → customer sees limit → merchant-payment demo → bill → repayment.
5. Book transfer → timeout lookup recovery → settled receipt; cancel an eligible payment.
6. Journal search → journal lines → trial balance → reconciliation item resolution.
7. Token expiry during a read and during a financial confirmation; no duplicate mutation occurs.
8. One dashboard service unavailable; other widgets remain usable and the error carries a correlation ID.

A page is done only when its authorization, API contract, loading/empty/error/forbidden states, responsive behavior, accessibility, audit/PII handling, automated tests, and product acceptance criteria are complete. A screen backed only by an internal or nonexistent endpoint is not done.

## 12. Immediate implementation backlog

1. Decide the final browser origin and update Identity redirect registrations and Gateway CORS together.
2. Create backend tickets for every P0 item, beginning with bill list/ownership and canonical card references.
3. Scaffold `moneybags-web` with the shell, authentication, API client, contract adapters, and common page-state components.
4. Implement one vertical slice first: sign-in → profile/KYC → product catalogue → account list. This validates security, headers, routing, form patterns, and service degradation before financial mutations.
5. Add financial transactions only after stable idempotency and timeout-lookup behavior is exercised through the Gateway.

## 13. Oracle JET reference baseline

- [Oracle JET 17 Get Started](https://docs.oracle.com/en/middleware/developer-tools/jet/17/index.html)
- [Oracle JET virtual DOM application workflow](https://docs.oracle.com/en/middleware/developer-tools/jet/17/vdom/understand-web-application-workflow.html)
- [Oracle JET Core Pack component guidance](https://docs.oracle.com/en/middleware/developer-tools/jet/17/develop/work-oracle-jet-user-interface-components.html)
- [Oracle JET Core Pack table and DataProvider](https://docs.oracle.com/en/middleware/developer-tools/jet/17.1/reference-api/oj-c.Table.html)
- [Develop accessible Oracle JET applications](https://docs.oracle.com/en/middleware/developer-tools/jet/17/develop/developing-accessible-applications.html)
