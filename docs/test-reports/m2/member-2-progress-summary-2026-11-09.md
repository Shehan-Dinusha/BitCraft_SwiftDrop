# Member 2 IAM contribution: complete progress summary

**Prepared:** 9 November 2026 (Asia/Colombo)  
**Branch:** `Nimsara/feat/IAM-Service`  
**Current branch tip:** `b197ce491da6a9891963b47fa74cbc51f8c3080e`  
**Remote branch:** `origin/Nimsara/feat/IAM-Service` points to the same commit.

This report summarizes the Member 2 IAM work completed so far, the verification recorded for it, and the remaining implementation and coordination work. It describes the repository state at the time of writing; the dated verification reports linked below preserve details from their respective runs.

## Current status

The planned IAM foundation and account-permission work are implemented, tested, documented, committed, and pushed to `Nimsara/feat/IAM-Service`. The branch contains 55 changed files relative to `dev` (`3,911` insertions and `57` deletions). The `dev` branch is currently at `34a6bad97465cc7497fc8c66a4e1fe9b5556500d`; the IAM branch is separate.

The workspace currently also has an untracked `library-api/` directory. It is outside the IAM contribution summarized here and is not included in the branch diff above.

## Work completed, in plan order

### 1. Authentication baseline

Recorded existing IAM and frontend behavior before fixes. The baseline confirmed normal registration, login, profile access, refresh, logout, password changes, rate limiting, role checks, and service health. It also captured two defects with reproducible cases:

- Replaying a rotated refresh token returned 401 but left its replacement refresh token usable.
- After a password change, a successful fresh login issued a token that was immediately rejected because the user-wide revocation marker also blocked newly issued sessions.

Baseline evidence: [authentication-baseline-2026-11-02.md](authentication-baseline-2026-11-02.md).

### 2. Authentication/session contract

Documented the agreed browser token-storage decision (protected refresh cookie and in-memory access token), current roles and claims, IAM ownership boundaries, account and district rules, session lifecycle, revocation semantics, and cross-member questions. This is a design and coordination document; the cookie-based browser migration itself is not complete.

Contract: [identity-and-session-contract.md](../../design/m2/identity-and-session-contract.md).

### 3. Session revocation defects

Fixed refresh-replay revocation so the revocation is committed when replay is rejected. Changed session cutoffs to distinguish tokens issued before a password/account change from a fresh login, including tokens issued in the same second. Preserved compatibility with existing `iat`-only tokens and legacy Redis blacklist markers. Added regression coverage in IAM, shared contracts, and the gateway cutoff reader.

The session work is covered by the test suites described in [authentication-session-fixes-2026-11-02.md](authentication-session-fixes-2026-11-02.md). The gateway change is limited to consuming the shared session-cutoff format needed by this IAM behavior; the wider gateway remains Member 1’s ownership area.

### 4. Shared caller identity and authorization helper

Added `CallerContext` to `backend/common-contracts`, with strict parsing for the authenticated user UUID, canonical backend role, optional merchant ID, and optional district. It provides explicit role, self-or-authorized-role, merchant ownership, and district-scope checks, plus typed 401/403 exceptions and usage guidance. Missing or malformed identity and missing resource scope fail closed; administrator access is not an implicit universal bypass.

The helper is a shared building block. It does not automatically secure the other services’ existing controllers; each service owner must adopt it at that service’s authorization boundary.

Evidence: [caller-context-2026-11-02.md](caller-context-2026-11-02.md).

### 5. IAM account and permission rules

- Kept `UserAccount.role` as the authority for token and API permissions; legacy role-assignment data does not grant authority.
- Rejected caller-supplied merchant associations during public registration and administrator creation. Trusted merchant approval/linking remains separate work.
- Required districts for courier and dispatcher identities; added a migration enforcing staff district requirements and one active dispatcher per district.
- Serialized sensitive staff administration with a transaction-scoped PostgreSQL advisory lock to protect the last active administrator under concurrent changes.
- Added district-scoped courier identity management for authorized dispatchers and system administrators: list, create, read, update contact details, and change account status.
- Kept courier operational data (vehicle, shifts, capacity, workload, and assignments) outside IAM, with its owning service/member.
- Updated the IAM API contract and regenerated the IAM frontend TypeScript declaration; the declaration was generated rather than hand-edited.

Evidence: [iam-account-permissions-2026-11-02.md](iam-account-permissions-2026-11-02.md).

### 6. Configurable RSA signing and JWKS

- Added RS256 as an option while retaining HS256 as the default for compatibility with the existing gateway.
- Validated configured PKCS#8 private and X.509 public RSA key pairs, required at least 2048-bit keys, and limited generated ephemeral keys to the explicit development profile.
- Added `kid` to RS256 tokens and pinned verification to the configured algorithm, key, issuer, and audience, with required identity/session-claim checks.
- Added the IAM JWKS endpoint publishing public RSA parameters only; HS256 mode without an RSA pair returns an empty key set and never publishes the HMAC secret.
- Synchronized the IAM signing settings in `.env.example`, Docker Compose, local/AKS IAM configuration, and Kubernetes deployment configuration.
- Updated the IAM OpenAPI file and regenerated only the IAM generated frontend declaration.

Rollout is intentionally not enabled by default: IAM remains on HS256 until the gateway can anonymously serve the JWKS route and validate RS256/JWKS tokens. Key overlap and rotation, cache refresh, and production unknown-key behavior still need a coordinated rollout design.

Evidence: [rs256-jwks-2026-11-02.md](rs256-jwks-2026-11-02.md).

## Verification recorded

The dated reports record these successful checks:

- Baseline IAM unit tests: 24 passed; shared-contract baseline: 1 passed.
- Session regression work: IAM 29 passed, shared contracts 6 passed, gateway authentication regression suite 6 passed; runtime checks were also recorded for the session fixes.
- CallerContext: 46 shared-contract tests passed; gateway and IAM compiled with the updated common module.
- Account/permission work: 55 IAM tests and 46 shared-contract tests passed; migration, OpenAPI/type generation, frontend lint/build, and live account-permission checks were recorded as passing.
- RSA/JWKS work: 84 IAM tests passed, including key loading, token validation, and HTTP session/JWKS integration tests. Frontend lint, type checks, build, and Compose configuration validation were recorded as passing.

The frontend build reports the repository’s existing large-chunk advisory. The account report records that an offline Maven `clean` attempt hit a missing local Maven plugin class; the standard package command passed without changing a POM or plugin version.

The latest RSA report says Docker became unavailable before the final RSA image restart and final HS256 gateway smoke check could be repeated. Treat the RSA JAR/test results as verified and the last container-level RSA rollout check as outstanding. No container claim should be inferred from compilation or unit tests alone.

## Preserved project constraints

- No dependency versions, Maven POM versions, frontend package versions, or `frontend/package-lock.json` were changed in this work.
- The recorded `frontend/package-lock.json` SHA-256 is `792E184E2914EC4819E62F80C887D04B428E11D88ADF7147F3638452358F4F72`.
- The recorded local `.env` matches `.env.example`; their recorded SHA-256 is `BCD3918751DA1B436EE3A1C891FFEE3EE784194D10D0FDD94C601086CA03B94C`.
- Container-based work was limited to the Member 2 development set documented in the reports. No other business-service containers or databases were requested for the IAM contribution.
- The work is on `Nimsara/feat/IAM-Service`; `dev` was not the implementation branch.

## Remaining work

### Required for a complete RSA production rollout

1. Coordinate with Member 1 to allow only `GET /api/v1/auth/.well-known/jwks.json` through the gateway without JWT authentication and to validate RS256 signatures using IAM’s JWKS.
2. Test the end-to-end gateway path with RS256 enabled, including key ID selection, rejection of invalid/unknown keys, and behavior during key/cache refresh.
3. Decide and implement a production key rotation and overlap procedure before replacing the active signing key.
4. Enable `JWT_ALGORITHM=RS256` only after the gateway is deployed and verified against the JWKS endpoint.

### Remaining Member 2/product integration work

- Implement the selected browser session model: refresh credential in a protected cookie and access token in memory, including CSRF/origin protections, refresh/logout behavior, frontend session restoration, and generated contract updates as coordinated with the gateway/frontend owner.
- Coordinate trusted merchant approval so IAM merchant associations can only be created by the approved workflow.
- Decide whether to add service-authenticated internal contact lookup and define its consumer/security contract.
- Continue the proposed Redis-unavailable policy review with Member 1; the design document records the proposed fail-closed production behavior, but it is not claimed as implemented.
- Have Members 3, 5, 6, 7, and 9 adopt the relevant identity/ownership checks in their own services and review the shared contracts. `CallerContext` availability alone does not complete those services’ authorization work.
- Add user-management UI only if it is in the agreed Member 2 scope; no such UI is claimed as implemented by this summary.

## Evidence and history

Detailed records are kept in:

- [Authentication baseline](authentication-baseline-2026-11-02.md)
- [Session fixes](authentication-session-fixes-2026-11-02.md)
- [Identity and session contract](../../design/m2/identity-and-session-contract.md)
- [CallerContext verification](caller-context-2026-11-02.md)
- [IAM account and permission verification](iam-account-permissions-2026-11-02.md)
- [RSA/JWKS verification](rs256-jwks-2026-11-02.md)

At the time of this summary, `HEAD` and `origin/Nimsara/feat/IAM-Service` are both `b197ce491da6a9891963b47fa74cbc51f8c3080e`. The branch contains the implementation commits and verification/documentation commits. The reports dated 2 November preserve the then-current run details; this 9 November summary records the later branch and remote status.
