# Member 2 step 4: IAM accounts and permissions

Date: 2 November 2026 (Asia/Colombo). Branch: `Nimsara/feat/IAM-Service`.

## Changes and placement

The implementation follows the existing IAM package structure:

| Location | Responsibility |
|---|---|
| `backend/auth-iam-service/.../controller/CourierAccountController.java` | Validated HTTP requests, existing response envelope and OpenAPI annotations |
| `backend/auth-iam-service/.../service/CourierAccountService.java` | Current-account authorization, shared CallerContext guards and courier contact management |
| `backend/auth-iam-service/.../dto/CreateCourierRequest.java`, `UpdateCourierRequest.java` | Courier request records; no operational dispatch fields |
| `backend/auth-iam-service/.../config/OpenApiConfig.java` | Explicit null-only request association schema because UUID inference does not express Jakarta `@Null` |
| `backend/auth-iam-service/.../domain/repository/UserAccountRepository.java` | IAM queries and transaction-scoped account-administration lock |
| `backend/auth-iam-service/src/main/resources/db/migration/V3__enforce_staff_district_and_dispatcher_uniqueness.sql` | New constraints; committed V1/V2 remain unchanged |
| `backend/auth-iam-service/src/test/java/com/swiftdrop/iam/service/` | Unit and opt-in real PostgreSQL/Redis regression tests |
| `docs/api-specs/auth-iam-service.yaml` | Updated static API contract |
| `frontend/src/types/generated/auth-iam-service.ts` | Regenerated IAM declarations, not hand-edited |

### Account rules

- Public registration remains CUSTOMER/MERCHANT only. Neither public registration
  nor administrator account creation may claim a merchant association. The field
  is retained for compatibility but must be absent/null. Trusted approval integration
  is separate later work. The demo seed script now omits the association.
- `UserAccount.role` is authoritative. Legacy role assignments remain stored but
  cannot grant token/API authority. Changing role clears stale merchant linkage.
- COURIER and DISPATCHER require districts. A partial unique index allows at most
  one ACTIVE DISPATCHER per district, including creation, promotion and reactivation.
- Last-active-administrator protection now distinguishes active/inactive records.
  A PostgreSQL transaction advisory lock serializes staff administration across IAM
  instances, preventing concurrent changes from removing all active administrators.
  This uses only the IAM database and adds no dependencies or tables.

### Courier endpoints

All paths below use the existing gateway IAM route. No gateway route changes:

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/v1/users/couriers` | Paginated search, restricted to dispatcher district or all districts for admin |
| POST | `/api/v1/users/couriers` | Creates COURIER identity in permitted district |
| GET | `/api/v1/users/couriers/{courierId}` | Reads scoped courier profile |
| PUT | `/api/v1/users/couriers/{courierId}` | Replaces email, phone and names; omitted/null phone clears it |
| PATCH | `/api/v1/users/couriers/{courierId}/status` | Suspends, deactivates or reactivates scoped courier |

These operations require an active DISPATCHER/SYSTEM_ADMIN from current IAM data,
identified by the authenticated JWT subject. Client-supplied identity headers do
not select privileges or scope. Unknown/non-courier IDs return 404; cross-district
access returns 403. Shared CallerContext exceptions retain their intended 401/403
status through IAM's error envelope.

Role/district changes are not available through courier contact updates. Vehicle,
capacity, shifts, workload and allocation remain Member 5's responsibility. An
email change revokes sessions carrying the previous email; suspending/deactivating
a courier revokes access/refresh sessions. A fresh legitimate login remains usable.

## Verification

| Check | Result |
|---|---|
| Shared-contract tests | 46 passed, zero failures/errors/skips |
| IAM tests | 55 passed, zero failures/errors/skips; includes 11 account-permission and 3 session integration tests |
| Focused backend package | Passed |
| Static OpenAPI generation | Passed |
| Frontend type checks and production build | Passed |
| Final live gateway/frontend checks | 41 passed, including null-only live schema parity |
| Database migration and fixture cleanup | V1/V2/V3 successful; zero accounts remain, matching the empty preflight |

Using Java 25 and existing local Maven artifacts:

```powershell
mvn -o -B -f backend/pom.xml -pl auth-iam-service -am -Dswiftdrop.integration-tests=true package
```

The integration tests opt in explicitly and use the existing IAM PostgreSQL/Redis.
They never create containers. They remove only their own uniquely named fixtures.
Run these opt-in account tests in a local development IAM database with no existing
active dispatcher in COLOMBO, which the fixtures use; the preflight confirmed an empty database.
The last-admin concurrency test skips if a real active administrator already exists,
so it never deactivates a user's account; it ran without skipping in this verification.

Coverage includes district-scoped list/create/read/update/status, global admin scope,
anonymous/customer denial, spoofed headers, current DB role changes, non-courier
targets, request validation, merchant injection, duplicate dispatcher HTTP 409,
reactivation/promotion conflicts, simultaneous dispatcher creation, simultaneous
administrator deactivation, canonical roles, session invalidation and the previous
refresh replay/password-change regression tests. The live-schema test verifies
that both creation DTOs expose a null-only merchant association, matching validation
and the checked-in OpenAPI contract.

Frontend generation and validation use a Docker build with the repository's
`node:22-alpine` image and `npm ci` against the unchanged lockfile:

```text
npm run api:sync:specs
npm run lint
npm run build
node --check scripts/seed-demo-users.mjs
```

All passed. Only the IAM generated declaration was copied back; other services'
generated files were not changed. The frontend build still reports the existing
large-chunk advisory.

The tested IAM package was rebuilt into its existing image using the repository's
`eclipse-temurin:25-jre-alpine` runtime. Only IAM was replaced, with Compose
`--no-deps --no-build --wait`. Health, OpenAPI and real authenticated requests were
verified through gateway `:8080` and frontend proxy `:5173`.
The final six-container inspection showed all services running; the five configured
health checks were healthy (frontend has no Compose health check).

An attempted offline `clean` goal encountered a missing Plexus class in the local
Maven clean-plugin cache. The standard offline `package` lifecycle above passed
after regenerating the affected output class. No plugin version or POM was changed.

## Preserved constraints and remaining work

- POMs, dependency versions, package manifests and `package-lock.json` unchanged.
- Lockfile SHA-256: `792E184E2914EC4819E62F80C887D04B428E11D88ADF7147F3638452358F4F72`.
- `.env` remains identical to `.env.example`; Compose and deployment files unchanged.
- Only the six existing Member 2 containers remain: IAM PostgreSQL, Redis, RabbitMQ,
  IAM, gateway and frontend. No other service/database containers were created.
- Existing step-2/3 work preserved. No commit, push or changes to `dev`.
- Cross-member agreement/downstream adoption is still pending. RSA/JWKS, browser
  cookie sessions, Google login, merchant approval events, internal contact lookup
  and user-management screens remain separate later steps.
- V3 deliberately fails on pre-existing duplicate active dispatchers or staff with
  missing districts rather than silently changing accounts. The local preflight
  found neither condition; migration succeeded.
