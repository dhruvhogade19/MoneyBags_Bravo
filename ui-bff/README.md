# Moneybags UI BFF

`ui-bff` is the single browser origin for the OJET application. It keeps OAuth access and refresh
tokens on the server and exposes only the `MONEYBAGS_UI_SESSION` and CSRF cookies to the browser.
Identity uses `MONEYBAGS_IDP_SESSION`; the distinct names prevent the two localhost applications
from overwriting each other's session during an OIDC redirect. Override them with
`UI_SESSION_COOKIE_NAME` and `IDENTITY_SESSION_COOKIE_NAME` if an environment requires it.
The BFF constructs its OIDC registrations from the configured issuer and standard Identity
endpoints without performing discovery during application startup. It can therefore serve the
public UI while Identity is restarting; sign-in still requires Identity to be available.

## Local URLs

- UI and BFF: `http://localhost:8000`
- Customer login: `/oauth2/authorization/moneybags-consumer`
- Bank administrator login: `/oauth2/authorization/moneybags-admin`
- Identity provider: `http://localhost:8093`
- Gateway: `http://localhost:8080`

The local Identity profile seeds these demonstration users unless they already exist:

| Role | Username | Default password variable |
|---|---|---|
| `BANK_ADMIN` | `admin@moneybags.local` | `LOCAL_ADMIN_PASSWORD` (`ChangeThisAdminPassword!`) |
| `CONSUMER` | `customer@moneybags.local` | `LOCAL_CONSUMER_PASSWORD` (`ChangeThisConsumerPassword!`) |
| `CONSUMER` compatibility seed | `consumer@moneybags.local` | `LOCAL_CONSUMER_PASSWORD` |

Change the defaults outside local demonstrations.

## Browser contracts

| Method and path | Authentication | Purpose |
|---|---|---|
| `GET /api/session` | Anonymous | Session, role, onboarding and CSRF state |
| `POST /api/registration` | Anonymous + CSRF | Register a login-capable `CONSUMER` identity |
| `POST /api/session/logout` | Session + CSRF | End local and OIDC sessions |
| `GET /api/public/products` and `/{code}` | Anonymous | Read active public catalogue entries |
| `/api/proxy/api/**` | Session + CSRF for mutations | Call public Gateway APIs with the server-held token |

`GET /api/session` returns `authenticated`, `username`, `roles`, `tenantId`, nullable `customerId`,
`onboardingStatus`, `clientRegistrationId`, `loginLinks`, and `csrf`. It also materializes the
`XSRF-TOKEN` cookie; send that cookie value in `X-XSRF-TOKEN` for POST, PUT, PATCH and DELETE.

A newly registered consumer is `PENDING_PROFILE` and can sign in to create a CIF. After CIF links
the Identity user, a newly issued token contains `customer_id` and the BFF reports `PENDING_KYC`.
For every customer banking proxy request, the BFF reads the linked CIF through the Gateway using
the server-held access token and signed tenant and requires its current `kycStatus` to be
`APPROVED`. A failed, malformed, or non-successful approval lookup denies the banking request.
Bank administrators bypass this customer gate and report `APPROVED` in the session contract.

Before approval, customers have this exact proxy allowlist; upstream ownership and scope checks
still apply:

- `POST /api/v1/cifs`, plus `GET` or `PUT /api/v1/cifs/{numericCifId}`
- `GET /api/v1/kycs`, `GET /api/v1/kycs/{numericKycId}`, and document metadata/download reads
- `POST /api/v1/kycs/{numericKycId}/documents`
- `GET /api/notifications` and `GET /api/notifications/{numericNotificationId}`
- `GET /api/products/**`, `GET /api/v1/products/**`, and `GET /api/benchmarks/**`

The proxy rejects every path outside `/api`, including encoded, nested, or traversal attempts to
reach `/internal`. It strips browser cookies and identity headers, injects the bearer token and
signed tenant, generates a UUID correlation ID, and preserves or generates `Idempotency-Key`.
Browser-provided `X-Actor-Id` is discarded. For administrators the BFF injects `X-Actor-Id` from
the signed OIDC `user_id`, falling back to signed `preferred_username`.

The OJET release output is loaded from `moneybags-ui/web` for repository-root launches or
`../moneybags-ui/web` for module launches. `MONEYBAGS_UI_STATIC_LOCATION` can point at another built
OJET directory without changing the application package.

Upstream calls use bounded defaults of 3 seconds to connect and 30 seconds to read. Override them
with `UI_UPSTREAM_CONNECT_TIMEOUT` and `UI_UPSTREAM_READ_TIMEOUT` when required.
