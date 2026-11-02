# Member 2 identity and session contract

Prepared: 2 November 2026 (Asia/Colombo).
Branch: `Nimsara/feat/IAM-Service`.
Implementation baseline: commit `68a01a9`.

This is step 2 of the Member 2 implementation plan. It defines the interfaces to
agree before the caller helper, account-permission changes, RSA/JWKS and Google
login are implemented. It does not change the running application.

## 1. Decision status

| Topic | Status | Decision or proposed contract |
|---|---|---|
| Browser token storage | Selected by Nimsara in this chat | Protected refresh cookie; access token in memory |
| Canonical roles and personas | Existing implementation | Keep the five backend roles and four frontend personas below |
| Current lifetimes | Existing configuration, retained | Access: 3,600 seconds; refresh: 30 days per issued refresh token |
| Session invalidation | Implemented and regression-tested | Refresh replay commits revocation; old access tokens are cut off without blocking a later login |
| RSA/JWKS rollout | Member 2 implementation ready; gateway rollout pending | IAM supports RS256 and public JWKS. HS256 remains the default until Member 1 enables gateway verification and anonymous JWKS access |
| Dispatcher/courier permissions | IAM account management implemented in step 4; downstream adoption pending | Dispatchers manage courier identities only in their district; assignment authorization stays with the owning service |
| Merchant linkage | Caller-supplied association rejected in step 4; approval integration and Members 7/9 review pending | Only trusted approval processing may establish the merchant association |
| Internal contact access | Proposed; Members 1 and 9 review pending | Service-authenticated lookup; no public gateway route |
| Production Redis failure policy | Proposed; Member 1 review pending | Return 503 when revocation cannot be checked; preserve current local behavior until implemented |

No teammate approval is implied. The browser-storage choice is confirmed by the
user; the cross-member decisions remain proposals for their owners to review.

## 2. Source and ownership

Use the [member ownership guide](../../member-guides/README.md),
[Member 2 guide](../../member-guides/member-2.md),
[IAM API baseline](../../api-specs/auth-iam-service.yaml), and
[session-fix evidence](../../test-reports/m2/authentication-session-fixes-2026-11-02.md)
alongside this contract.

| Area | Owner |
|---|---|
| Accounts, passwords, login, refresh and token issuance | Member 2 |
| Shared identity headers, role/status enums and caller helper | Member 2 |
| Gateway identity sanitization, public paths, JWT/STOMP validation | Member 1 |
| Customer order ownership checks | Member 3 |
| District queues, operational courier data and allocation checks | Member 5 |
| Delivery assignment ownership and courier transitions | Member 6 |
| Merchant approval, business account state and API-key authorization | Member 7 |
| Shared event contracts/topology and notification integration | Member 9 |

Identity belongs to IAM; business-record authorization belongs to the owning
service. A browser route guard or valid JWT never replaces those service checks.
All browser REST calls continue through `/api/v1/...` on the gateway, directly or
through the existing frontend proxy. Services never query another service's DB.

## 3. Roles, account creation and districts

Exactly one backend role is authoritative for each user. Newly issued tokens use
the canonical values below. `ADMIN_OPS` is a UI persona, not an administrator role.

| Backend role | UI persona | Home | Target account creation | Authorization scope |
|---|---|---|---|---|
| `CUSTOMER` | `CUSTOMER` | `/customer` | Self-registration or Google as a new customer | Own orders/profile |
| `MERCHANT` | `MERCHANT` | `/merchant` | Self-registration; business approval handled separately | Own approved business and shipments |
| `COURIER` | `COURIER` | `/courier` | Own-district dispatcher; administrator retains global provisioning | Own assigned deliveries and own operational data |
| `DISPATCHER` | `ADMIN_OPS` | `/ops` | System administrator | Own district only |
| `SYSTEM_ADMIN` | `ADMIN_OPS` | `/ops` | Initial bootstrap; later provisioning by authorized system administrator | Explicitly authorized global administration |

The frontend retains the raw backend role alongside the persona. Admin-only pages
must require `SYSTEM_ADMIN`, not simply `ADMIN_OPS`. Unknown roles must not grant a
default privilege. Existing legacy role aliases are a migration concern, not an
allowed role vocabulary for new clients or newly issued tokens.

Dispatcher and courier accounts require a valid operating district. Districts use
the existing `SriLankanDistrict` enum values, such as `COLOMBO` and `KANDY`.
Enforce at most one active dispatcher per district, including concurrent account
changes. A district can temporarily have none; initial creation of all 25 is not
an automatic seed operation. Customer pickup/delivery districts are order data,
not an authorization boundary derived from the user's home district.

IAM owns credentials, role, identity status and assigned district. Member 5 owns
vehicle, capacity, shifts and workload. A dispatcher cannot create administrators,
change a courier into a privileged role, or manage a courier in another district.

Step 4 implements courier identity management at `/api/v1/users/couriers`:
`GET` lists scoped couriers, `POST` provisions a courier, `GET /{courierId}` reads
one, `PUT /{courierId}` replaces contact details, and
`PATCH /{courierId}/status` changes account status. An active dispatcher may act
only in their current IAM district; an administrator may act across districts.
The authenticated user ID loads the current IAM role and district; browser-supplied
identity headers do not grant authority. Role, district and dispatch operational
fields cannot be changed through these courier endpoints.

The existing administrative `/api/v1/users` endpoints remain administrator-only.
Creation requests continue to require a district. Flyway V3 enforces a non-null
district for couriers/dispatchers and one active dispatcher per district, including
promotion and reactivation. IAM staff administration uses a transaction-scoped
PostgreSQL advisory lock so simultaneous changes cannot remove the final active
administrator. Inactive administrator records do not incorrectly trigger that guard.

`UserAccount.role` is the sole authoritative role. The legacy `role_assignments`
table is retained for compatibility and does not supply token or API authorities.
Registration and administrative creation reject a supplied `merchantId`; a role
change clears an existing merchant association and revokes sessions. Trusted
merchant approval processing remains a later integration.

`UserStatus` remains `ACTIVE`, `SUSPENDED` or `DEACTIVATED`. Only active users can
login or refresh. Preserve the last-active-administrator guard. Business merchant
approval is separate from IAM account status.

## 4. JWT identity and gateway headers

Preserve current claim names during subsequent implementation:

| Claim | Representation | Meaning |
|---|---|---|
| `sub` | Public user UUID string | Stable caller identity; never email or internal bigint ID |
| `jti` | Unique token identifier | Individual access-token revocation |
| `iss` | `swiftdrop-iam` | Issuer; retained from current configuration |
| `aud` | Contains `swiftdrop` | Audience; retained from current configuration |
| `iat`, `exp` | JWT NumericDate | Issuance and expiry |
| `sessionIssuedAt` | ISO-8601 UTC instant with available subsecond precision | Shared access-token cutoff comparison |
| `role` | One canonical `UserRole` | Authorization role |
| `districtId` | District enum name when assigned | District authorization scope; this is not a district UUID |
| `merchantId` | Merchant UUID when trusted association exists | Business ownership association |
| `username`, `email` | Existing identity fields | Compatibility fields; do not use as authorization identifiers |

Do not add phone numbers, addresses, passwords or Google tokens to JWTs. Retain
existing username/email compatibility until a separately coordinated removal;
clients must already treat tokens as sensitive and avoid logging them.

Protected requests use `Authorization: Bearer <access-token>`. The gateway strips
caller-supplied identity headers and recreates them only from verified identity:

| Header | Source | Requirement |
|---|---|---|
| `X-User-Id` | `sub` | Required for an authenticated user |
| `X-User-Role` | `role` | Required; canonical backend value |
| `X-District-Id` | `districtId` | Required for district-scoped staff operations |
| `X-Merchant-Id` | Trusted `merchantId` or verified merchant API-key identity | Required for operations on that business |
| `X-Correlation-Id` | Valid request correlation ID or generated ID | Preserve through downstream calls/events |

Public paths and API-key ingestion must also prevent spoofed identity headers;
API-key validation establishes its own merchant identity. Do not forward browser
claims as trusted headers merely because the URL is public.

## 5. CallerContext contract for plan step 3

The implemented immutable caller context contains `userId` (UUID), `role` (`UserRole`),
optional `merchantId` (UUID), and optional `districtId` (`SriLankanDistrict`).
The [helper usage guide](../../../backend/common-contracts/README.md) documents
`from(Function<String, String>)`, servlet/reactive adapters, guards and required
exception mapping. Service adoption is a separate step; the library does not
automatically secure existing controllers.

The core parsing/checking logic must not require servlet classes, since
`common-contracts` is also consumed by the reactive gateway. Use a neutral header
lookup in the core and an appropriate adapter at servlet request boundaries.

Required checks are role membership, self-or-authorized-role, merchant ownership,
and district equality. A helper must not silently default a missing role, accept a
malformed UUID, or interpret a missing district as global scope. Service owners
still load their record and check its actual customer/courier/merchant/district.

An administrator bypass must be explicit for each operation. Do not make every
`SYSTEM_ADMIN` request an automatic bypass of all business rules.

## 6. Selected browser session model

**Future target selected by Nimsara:** opaque refresh token in a protected cookie;
access token in memory. Current code still returns both tokens in JSON and stores
them in `sessionStorage`. That implementation remains unchanged in this step.

The proposed cookie is `swiftdrop_refresh`, host-only (no `Domain` attribute),
`HttpOnly`, `SameSite=Lax`, `Path=/api/v1/auth`, and `Secure` over production HTTPS.
Max age follows the configured refresh lifetime (30 days currently). A narrowly
scoped local-HTTP exception to `Secure` is needed for the existing localhost setup;
it must not become the production default.

The access token is not persisted to localStorage, sessionStorage, URLs or a
readable cookie. Reloading the app restores the session through refresh, then
loads `/api/v1/users/me`. The frontend renders a loading state until restoration
finishes, rather than briefly showing a protected page.

Because cookies are automatically attached by browsers, adopting this target
requires CSRF protection. Proposed browser contract: a separate readable CSRF
token and matching `X-XSRF-TOKEN` header on cookie-session POST operations, plus
strict trusted-Origin checks. The CSRF token is not the refresh credential.
Document the token bootstrap mechanism before implementing it. Do not carry the
current blanket CSRF disable into cookie-session endpoints.

Prefer the existing same-origin frontend proxy. If another browser origin must be
supported, use an explicit origin allowlist and credential-aware requests/CORS;
wildcard credentialed origins are not part of this contract. Members 1 and 2 must
agree proxy handling of cookies, CSRF headers and forwarded origin information.

## 7. HTTP session lifecycle and migration

These are proposed browser behaviors for coordinated backend/frontend delivery:

| Endpoint | Target behavior | Current compatibility difference |
|---|---|---|
| `POST /api/v1/auth/register` | Create a customer or merchant identity; no staff self-registration or caller-selected merchant association | Public request currently accepts `merchantId`; remove/reject it in account work |
| `POST /api/v1/auth/login` | Verify password/status, set refresh cookie, return the access-token response | Currently includes refresh token in JSON |
| `POST /api/v1/auth/refresh` | Read and rotate refresh cookie, set replacement cookie, return new access token | Currently requires `refreshToken` in JSON |
| `POST /api/v1/auth/logout` | Revoke current refresh credential and supplied access token; clear cookie and browser state | Current refresh credential is supplied in JSON; endpoint requires access authentication |
| `POST /api/v1/auth/change-password` | Verify current password, change hash, revoke existing sessions; client returns to login | Existing account-wide invalidation is implemented and tested |
| `GET /api/v1/users/me` | Return the authenticated user's current profile | Already exists |

The target token response retains the existing flat fields `accessToken`,
`tokenType`, `expiresIn`, `userId`, `username`, `email`, `role`, and `district`.
It omits the raw `refreshToken`. Do not change the response envelope incidentally:
login/refresh currently return their token DTO directly; profile/registration use
`ApiResponse<T>`.

Logout should be able to clear/revoke a valid refresh cookie even when the access
token has expired. Members 1 and 2 must coordinate that route/security change and
its CSRF checks. Expired or invalid cookie credentials must still be cleared
without disclosing account details.

Do not silently fall back to a JSON refresh credential if a browser refresh cookie
is present. Browser clients should stop using the JSON refresh field when cookie
mode is enabled. Any retained non-browser token endpoint must have an explicit
separate client contract; it is not implicitly introduced by this document.

The frontend shares one in-flight refresh and retries a failed protected request
at most once. Because cookies are shared across tabs, coordinate rotations across
tabs too; simultaneous normal requests must not be mistaken for stolen-token
replay. A 403 does not trigger refresh. Refresh rejection clears the local session.
A transient 503 is treated as unavailability rather than proof of logout.

Cookie migration must be coordinated with IAM security, the gateway, the frontend,
OpenAPI, generated types and configuration. Do not remove current JSON fields or
change allowlists until the replacement path has tests and consumers are ready.

## 8. Lifetime, revocation and failure rules

Keep the configured one-hour access and 30-day refresh lifetimes for this plan.
Current refresh rotation renews the replacement token's expiry; no absolute
account-session maximum is currently defined. Do not claim a fixed 30-day total
session lifetime without implementing and agreeing that additional rule.

Refresh credentials remain opaque, random and hashed in IAM's PostgreSQL DB.
Redis holds access-token revocation/cutoffs and login-limit state; it does not
replace PostgreSQL refresh storage. Rotation is atomic. Reuse of a replaced
refresh credential revokes all existing refresh credentials for that user and
cuts off previously issued access tokens. Legitimate later login remains possible.

Password changes, role changes and deactivation invalidate existing sessions.
Suspended/deactivated accounts cannot obtain replacement tokens. Repeated account
changes must not reinstate an older token. Account-wide invalidation is distinct
from current logout, which revokes the presented refresh credential and access
token; other access tokens are not automatically a logout-all-devices feature.

HTTP IAM and gateway share `JwtSessionRevocation`. Preserve `sessionIssuedAt`,
standard-iat fallback and legacy Redis-marker compatibility through RSA migration.
Before considering private STOMP sessions equivalent to HTTP, Member 1 must add
equivalent issuer/audience/revocation checks and define invalidation of already
connected private subscriptions. Public sanitized tracking remains anonymous.

Current local Redis errors fail open in revocation/login checks. Proposed
production policy is 503 for protected operations when required revocation state
is unavailable, with no new tokens issued when required session state cannot be
persisted. Member 1 review and explicit profile/configuration work are required;
this document does not change the current policy.

## 9. RS256 and JWKS handoff

Member 2 owns signing keys, token construction shared by password/Google login,
and `GET /api/v1/auth/.well-known/jwks.json`. The public document contains RSA public
parameters only. Tokens carry `kid`; the private key never reaches gateway,
frontend, source control, API responses or logs.

Member 1 owns the HTTP and private-STOMP verifier changes and public JWKS route.
Verify the configured algorithm, signature, known key, issuer, audience, expiry,
not-before when present, required identity claims and revocation. Do not choose an
allowed algorithm solely because the token header requests it.

IAM now has explicit `JWT_ALGORITHM`, `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY`
configuration. HS256 remains the checked-in local/AKS default. RS256 requires a
matching RSA pair outside the development profile; development can create an
ephemeral 2048-bit key pair when no pair is configured. IAM signs RS256 tokens with
the public key fingerprint as `kid`, and publishes RSA public parameters at
`/api/v1/auth/.well-known/jwks.json`. The endpoint returns an unwrapped JWKS with
a five-minute public cache policy; HS256 mode without a configured RSA pair returns
an empty set and never publishes `JWT_SECRET`.

The gateway route already forwards `/api/v1/auth/**`, but its filter currently
requires a token before forwarding the JWKS request. Member 1 must allow only the
GET JWKS path anonymously and deploy an RS256 verifier that checks signature,
known `kid`, issuer, audience, expiration, required identity claims and revocation.
Coordinate that rollout before setting `JWT_ALGORITHM=RS256` outside local tests.
The current IAM key provider publishes one key and rejects unknown IDs; overlapping
key rotation and cache refresh policy remain future work. Do not retire a key while
its legitimate access tokens remain valid. The three environment values are
synchronized across `.env.example`, Compose and the IAM deployment manifest.

## 10. Google identity handoff

Use the design's authorization-code flow with state, nonce and PKCE. Redis login
transactions are short-lived and consumed once. Validate Google's identity token
before trusting its subject/email. Store provider subject linkage separately from
the mutable email address. A new Google identity creates a `CUSTOMER`; an existing
linked identity keeps its saved role and must still be active.

An existing matching email alone does not authorize silent account linking.
Require a deliberate authenticated linking flow or reject with linking guidance.
Settle the data model for Google-only accounts before changing the mandatory
password hash. Google access/refresh tokens never become SwiftDrop session tokens.

The frontend callback may exchange a short-lived, single-use code, then receive
the selected cookie/in-memory session. Thus the guide's one-time code and the
design's protected cookie can coexist. Never put application tokens in redirect
URLs. CSRF bootstrap, error redirects and callback allowlists are part of the
coordinated browser contract, not implementation already present today.

## 11. Merchant approval contract

The proposed rule is administrator approval owned by Member 7, following the SRS.
A merchant may login to its pending/onboarding view, but cannot create orders or
obtain usable merchant API access before approval. An active IAM account is not
proof that its business is approved.

Public registration cannot choose an existing `merchantId`. Members 7 and 9 supply
a trusted `merchant.approved` event containing at least `eventId`, `correlationId`,
`merchantId`, `ownerUserId`, and event time. Exact record field names, queue/DLQ and
status-change ordering/version rules must be frozen with those members before the
consumer is added. The current event catalogue has no approval event yet.

IAM verifies the intended merchant-role owner, records the association in its own
DB, and records the event ID atomically so redelivery is harmless. It must not
silently transfer a user between businesses. New/renewed claims reflect the
trusted association; existing tokens lacking it do not gain permissions.

Member 7 must still check current business approval and ownership on protected
business operations. Agree rejection/revocation events and stale-event handling;
an older approval must not undo a later revocation. Account-state changes and
business-state changes are different contracts.

## 12. Internal contact lookup

Proposed endpoint: `GET /internal/v1/users/{userId}/contact`, called by the
notification service using authenticated service identity. Do not expose this
path in public gateway routes. Network placement or a hidden URL is not sufficient
authentication. Members 1 and 9 must agree the service identity mechanism and
minimum authorized caller before implementation.

Proposed successful response uses the existing envelope:

```json
{
  "success": true,
  "message": "Contact retrieved",
  "data": {
    "userId": "11111111-1111-4111-8111-111111111111",
    "email": "recipient@example.invalid",
    "phone": null
  },
  "timestamp": "2026-11-02T00:00:00Z"
}
```

Fields are limited to public user ID, email and optional phone. Do not include
password hashes, tokens, full profiles or roles. Unknown users return 404.
Notification delivery to an inactive account is a business policy for Member 9 to
confirm; it must not be confused with permission to authenticate that account.
Receivers are not registered users: their contact information must come from the
owning order workflow, not from fabricated IAM user identities.

## 13. Errors and acceptance criteria

Use `ApiErrorResponse` rather than bespoke error objects. Keep credentials, tokens
and contact data out of diagnostic messages. Contract status rules:

| Status | Meaning |
|---|---|
| 400 | Invalid registration/input or malformed request |
| 401 | Missing/invalid/expired/revoked application credential or invalid required identity |
| 403 | Valid caller lacks permission; inactive account login remains denied |
| 404 | Missing resource, or hidden ownership failure where the service contract requires it |
| 409 | Identity uniqueness conflict or protected last-administrator/district invariant |
| 429 | Login failure/rate limit reached |
| 503 | Required authentication dependency unavailable under the proposed production policy |

Examples to verify as each owning step is implemented:

- A customer can read their profile but gets 403 on administrator-only operations.
- A dispatcher can manage a courier in their district, not another district.
- A courier cannot read or mutate another courier's assigned delivery.
- Missing district context never widens a dispatcher's scope.
- Merchant registration cannot bind someone else's business; duplicate approval
  events do not duplicate or transfer the association.
- Refresh replay commits revocation, and a legitimate fresh login still works.
- Password change rejects old sessions while immediately permitting the new login.
- Cookie refresh works after reload; CSRF/Origin checks reject forged requests.
- Parallel frontend requests/tabs do not accidentally reuse a rotated credential.
- RSA/JWKS verification works in HTTP and private STOMP; public tracking stays
  anonymous and contains only sanitized data.
- Unauthenticated service contact requests fail and the public gateway has no
  contact route.

## 14. Review handoff and next work

| Review owner | Concrete items to confirm | Status |
|---|---|---|
| Nimsara | Protected refresh cookie and in-memory access token | Selected |
| Member 1 | Claims/headers; RSA/JWKS; cookie/CSRF forwarding; private STOMP revocation; Redis production policy | Pending |
| Member 5 | District staff-provisioning split and one-active-dispatcher rule | Pending |
| Member 6 | Courier ownership checks using shared caller context | Pending |
| Member 7 | Merchant approval authority, owner linkage and revocation policy | Pending |
| Member 9 | Approval event schema/order/idempotency and service-authenticated contact response | Pending |

This file is the reviewable step-2 deliverable. Cross-member agreement is not
complete until those owners confirm their rows. No messages have been sent to
them automatically. Record later decisions here with their actual date/owner.

Plan step 3 now supplies the neutral caller-context helper and its tests.
Plan step 4 now supplies the IAM account and permission changes above; see
[verification evidence](../../test-reports/m2/iam-account-permissions-2026-11-02.md).
Coordinate downstream adoption with the relevant owners. Cookie, RSA, Google,
merchant approval events and internal service contact access remain later work.
Existing signing, browser token storage and environment configuration remain in
place until their coordinated migration is ready. IAM API types have been regenerated.

Dependency versions and `package-lock.json` are fixed constraints. This contract
authorizes no dependency addition, upgrade, environment change, service startup or
new container. Any later required configuration must use the repository's existing
version declarations and synchronized environment/deployment files.
