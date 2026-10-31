# Member 2: Nimsara · Identity & Access Management (IAM)

> Draft guide, written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`).
> Your assignment list is the source of truth for **what** you build. The repo is the source of truth for **how** (stack, folders, ports, names).

## 0. Your scope at a glance

| Item | Value |
|---|---|
| Backend module | `backend/auth-iam-service` |
| Local app port | `8081` |
| Database | `iam-postgres` container, host port `5431 (compose) — see gap`, database `swiftdrop_iam` |
| Frontend folders you own | `frontend/src/features/auth/`, `src/app/providers/AuthProvider.tsx`, `src/store/useAuthStore.ts`, `src/services/api/apiClient.ts`, `src/services/api/authApi.ts`, `src/app/RoleGuard.tsx` (new), `features/admin-ops/pages/UserManagementPage.tsx` (new) |
| Documentation deliverable | Sequence diagram for Google OAuth 2.0 login + JWT lifecycle specification |
| Branch prefix | `feature/m2-<short-topic>` (off `dev`, PR back into `dev`) |

Read [README.md](README.md) in this folder once. It has the shared-file ownership map and the list of cross-member contracts.

## 1. What already exists on `dev`

- `auth-iam-service`: register, login, refresh, logout, change-password (`AuthController`); admin user CRUD/role/status (`UserRoleController`, `@PreAuthorize("hasRole('SYSTEM_ADMIN')")`); `PasswordPolicy`, `LoginAttemptService` (Redis), refresh tokens table, `AdminBootstrap`.
- JWT is **HS256** with a shared `JWT_SECRET` (`TokenIssuanceService` uses `Keys.hmacShaKeyFor`). Claims: `role`, `username`, `email`, `districtId`, `merchantId`.
- Roles enum `UserRole`: `CUSTOMER, MERCHANT, COURIER, DISPATCHER, SYSTEM_ADMIN`. Frontend uses a different `PersonaRole`: `CUSTOMER, COURIER, MERCHANT, ADMIN_OPS`.
- Tests: `AuthenticationServiceTest`, `PasswordPolicyTest`, `RefreshTokenServiceTest`, `TokenIssuanceServiceTest`, `UserAccountServiceTest`.
- Frontend: `LoginPage`, `PersonaSwitcherDevBar`, `AuthProvider`, `useAuthStore`, `apiClient.ts`; `RoleGuard` lives inside `routes.tsx`.

## 2. Gaps to close

- [ ] **Google OAuth 2.0 is missing entirely** (no `oauth2` dependency, no Google button).
- [ ] **RS256 is required by your list.** Switch signing to an RSA key pair, expose the public key as JWKS (`GET /api/v1/auth/.well-known/jwks.json`), keep `kid` in the header.
- [ ] **Password hashing:** confirm BCrypt (strength 12) or Argon2 is used in `SecurityConfig`, and document it.
- [ ] **Token revocation in Redis:** a blacklist prefix exists in `JwtConfig`; confirm logout writes the token `jti` with TTL = remaining lifetime, and the gateway checks it.
- [ ] **Role mapping:** frontend `ADMIN_OPS` vs backend `DISPATCHER` + `SYSTEM_ADMIN`. Decide one mapping and implement it in the auth store.
- [ ] **Hard-coded credentials and port mismatch.** `application.yml` hard-codes `jdbc:postgresql://localhost:5432/swiftdrop_iam`, user `swiftdrop`, password `swiftdrop` (no env variables), and compose exposes `iam-postgres` on `5431`. The assignment forbids hard-coded secrets: switch to `${DB_URL:...}`/`${DB_USERNAME:...}`/`${DB_PASSWORD:...}` like the other services and agree the port with Member 1.
- [ ] **Caller-identity helper** for the other 8 services (they don't use Spring Security; they read gateway headers). See Phase 1.
- [ ] **Merchant id claim:** the token already carries `merchantId`, but nothing sets it. Consume Member 7's `merchant.approved` event to store it.
- [ ] **User management screen** for admins (assignment: "Manage users").
- [ ] Frontend interceptors: attach bearer token, refresh on 401 once, redirect on 403.

## 3. Boundaries

**You own:** `backend/auth-iam-service/`; `common-contracts/security/*` (`JwtHeaderConstants`, the new `CallerContext`); enums `UserRole`, `PersonaRole`, `UserStatus`; the frontend auth files listed above.

**You do not touch:** gateway filters (Member 1 changes them to RS256 using your JWKS); other services' controllers (they adopt your helper themselves).

## 4. Who you depend on

| Member | What you need | Until it arrives |
|---|---|---|
| Member 1 (Shehan) | Gateway verifies RS256 via your JWKS; `/ws/tracking` token check updated | Ship RS256 behind `JWT_ALGORITHM=RS256`, default `HS256` until both PRs are merged |
| Member 7 (Uditha) | `merchant.approved` event `{merchantId, ownerUserId}` | Admin can set `merchantId` via your user PATCH endpoint |
| Member 9 (Umesh) | Adds the `merchant.approved` routing key and queue binding to contracts/definitions | Declare the queue in your own `RabbitMQConfig` |

Others depend on you for: `CallerContext` helper (Phase 1), JWKS URL, final claim names, `RoleGuard`, `apiClient` interceptors, a `GET /internal/v1/users/{id}/contact` for Member 9.

## Part A. One-time setup (about 1 hour)

**A1. Install tools.** Java 25 (Temurin), Maven 3.9+, Node.js 20+, Docker Desktop, Git, VS Code with *Extension Pack for Java* and *Spring Boot Extension Pack*. Check them:

```powershell
java -version; mvn -version; node --version; npm --version; docker --version; docker compose version
```

**A2. Clone and branch from `dev`** (never work on `main`):

```powershell
git clone https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop.git
cd BitCraft_SwiftDrop
git checkout dev
git pull origin dev
git checkout -b feature/m2-setup
```

**A3. Environment file.**

```powershell
Copy-Item .env.example .env
```

Never commit `.env`. Every new variable you add goes into `.env.example`, `docker-compose.yml` and `infra/k8s/services/auth-iam-service.yml` (rule from `AGENTS.md`).

> **Known setup traps (skip this box once `repo-fix-plan.md` is merged on `dev`):**
> 1. `docker-compose.yml` starts Postgres with password `changeme_pg`, but services default to `swiftdrop`.
> 2. Compose creates the RabbitMQ user `swiftdrop`/`changeme_rabbit`, but most services set no RabbitMQ user, so Spring falls back to `guest`/`guest` and cannot connect.
>
> In each terminal where you run your service, set these first:
>
> ```powershell
> $env:DB_PASSWORD = 'changeme_pg'
> $env:SPRING_RABBITMQ_USERNAME = 'swiftdrop'
> $env:SPRING_RABBITMQ_PASSWORD = 'changeme_rabbit'
> ```
>
> Then, in your first PR, add `username: ${RABBITMQ_USERNAME:swiftdrop}` and `password: ${RABBITMQ_PASSWORD:changeme_rabbit}` under `spring.rabbitmq` in **your** `application.yml`.

**A4. Install the shared contracts jar** (repeat whenever `backend/common-contracts` changes on `dev`):

```powershell
mvn -f backend\pom.xml -pl common-contracts -am -DskipTests install
```

**A5. Install frontend packages.**

```powershell
cd frontend; npm install; cd ..
```

**A6. Start only the infrastructure you need.**

```powershell
docker compose up -d rabbitmq iam-postgres redis
docker compose ps
```

RabbitMQ UI: http://localhost:15672 (user `swiftdrop`, password `changeme_rabbit`).

**A7. Run your service and confirm it boots.**

```powershell
mvn -f backend\pom.xml -pl auth-iam-service -am spring-boot:run
```

Open http://localhost:8081/actuator/health and http://localhost:8081/swagger-ui.html.

**A8. Record today's baseline.** Run your module's tests once and write down what passes and fails before you change anything:

```powershell
mvn -f backend\pom.xml -pl auth-iam-service -am test
```

**A9. Read before coding:** `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `docs/LOCAL-DEVELOPMENT.md`, `frontend/COMPONENTS.md`, and `.agents/rules/*.md`. They define package layout, DTO rules (Java records + `@Schema`), Flyway rules (new `V<N>__*.sql`, never edit an old one), and the Velocity design-system rules.

> **Your service is different:** `auth-iam-service/application.yml` hard-codes the DB URL (port 5432), user and password, and RabbitMQ defaults to `guest`/`guest`. Until you fix that (Phase 1), override them with Spring's standard variables instead of `DB_PASSWORD`:
>
> ```powershell
> $env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5431/swiftdrop_iam'
> $env:SPRING_DATASOURCE_PASSWORD = 'changeme_pg'
> $env:RABBITMQ_USERNAME = 'swiftdrop'; $env:RABBITMQ_PASSWORD = 'changeme_rabbit'
> ```

## Part B0. How you work every day (same for all members)

1. Start from fresh `dev`: `git checkout dev; git pull origin dev; git checkout -b feature/m2-<topic>`.
2. Work stage by stage (Part B): **one stage = one commit**, one group of stages = one PR. Small commits are easy to review and to revert.
3. Commit messages follow the style already on `dev`: `feat(<scope>): ...`, `fix(<scope>): ...`, `test(<scope>): ...`, `docs(<scope>): ...`.
4. Only edit files you own (section "Boundaries"). If you need a change in someone else's file, message that member with the exact change, or open a tiny PR and ask them to review it.
5. Before pushing: compile and test your module, and if you touched the frontend run `npm run lint` and `npm run build` in `frontend/`.
6. Open the PR into `dev`, ask at least one teammate to review, and merge only when it builds.
7. After `common-contracts` changes land on `dev`, everyone re-runs step A4.

## Part B. Stage-by-stage implementation plan

Each **stage** below is one small, reviewable commit. Finish a stage, check its *Done when* line, then commit with the suggested message. Stages are grouped into **PRs**: push your branch after every stage, and open the PR when the group is done.

Rules:
- One stage = one commit. Don't mix unrelated changes (e.g. a refactor and a new endpoint) in one commit.
- Commit only when the code compiles and the stage's check passes. Never commit broken code to save work; use `git stash` instead.
- If a stage grows past ~300 changed lines, split it and commit the first half.
- Tests live in the same stage as the code they test, unless the stage is explicitly a test stage.
- Branch per PR: `feature/m2-<pr-topic>` from fresh `dev`.

> **Before you start:** `repo-fix-plan.md` step 1 (credentials and the IAM port) is being done separately. Pull `dev` once it's merged before Stage 1.


### PR 1: Unblock the team


#### Stage 1: CallerContext helper

**Objective:** Give all services one way to read the caller.

**Steps:**

1. Add `common-contracts/security/CallerContext` record (`userId`, `role`, `merchantId`, `districtId`) with `static CallerContext from(HttpServletRequest)` reading the `X-User-*` headers from `JwtHeaderConstants`.
2. Add `requireRole(UserRole...)` and `requireSelfOrRole(UUID, UserRole...)` throwing a `ForbiddenException` (new, in the same package).
3. Unit test with `MockHttpServletRequest`.

**Commit:** `feat(contracts): add CallerContext for gateway identity headers`

**Done when:** `mvn -f backend\pom.xml -pl common-contracts -am install` passes; post the usage example in the team chat.


#### Stage 2: Move RoleGuard

**Objective:** Own the guard in its own file.

**Steps:**

1. Move `RoleGuard` from `routes.tsx` to `src/app/RoleGuard.tsx` and import it (coordinate with Member 1's route-placeholder PR, after it merges).

**Commit:** `refactor(frontend): extract RoleGuard component`

**Done when:** `npm run build` passes; routes behave as before.


#### Stage 3: Role mapping

**Objective:** Frontend and backend agree on roles.

**Steps:**

1. Map backend `DISPATCHER` and `SYSTEM_ADMIN` to the frontend persona `ADMIN_OPS` in `useAuthStore` (keep the raw role too for admin-only screens).
2. Document the mapping in a comment.

**Commit:** `fix(frontend): map backend roles to personas`

**Done when:** Log in as a dispatcher: lands on `/ops`.


### PR 2: Token lifecycle on RS256


#### Stage 4: RSA key loading

**Objective:** Prepare RS256 signing keys.

**Steps:**

1. Add `JwtKeyProvider`: load PEM from `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`; if blank and profile is dev, generate an in-memory 2048-bit pair and log a warning (never the key).
2. Unit test both paths.

**Commit:** `feat(iam): load RSA signing keys from environment`

**Done when:** Service boots with and without the env vars.


#### Stage 5: Sign with RS256

**Objective:** Switch token signing behind a flag.

**Steps:**

1. `TokenIssuanceService`: if `JWT_ALGORITHM=RS256` sign with the private key and set header `kid`; else keep HS256.
2. Extend `TokenIssuanceServiceTest` for both modes, tampered signature, expired token.

**Commit:** `feat(iam): sign access tokens with RS256`

**Done when:** Tests pass; jwt.io shows `alg: RS256` and `kid`.


#### Stage 6: JWKS endpoint

**Objective:** Let the gateway verify tokens.

**Steps:**

1. `GET /api/v1/auth/.well-known/jwks.json` returns the public key as JWK.
2. Ask Member 1 to add it to gateway `PUBLIC_PATHS`; send them the URL.

**Commit:** `feat(iam): expose JWKS endpoint`

**Done when:** `curl` returns a JWK with `kty: RSA` and the same `kid`.


#### Stage 7: Refresh rotation

**Objective:** Refresh tokens can't be reused.

**Steps:**

1. On `/refresh`, revoke the old refresh token and issue a new one; reuse of a revoked one revokes the whole family.
2. Extend `RefreshTokenServiceTest`.

**Commit:** `feat(iam): rotate refresh tokens on use`

**Done when:** Using the same refresh token twice fails the second time.


#### Stage 8: Logout blacklist

**Objective:** Logged-out tokens stop working.

**Steps:**

1. On logout write `jti` to Redis with TTL = remaining lifetime (prefix `JWT_BLACKLIST_PREFIX`).
2. `LogoutBlacklistTest` with Testcontainers Redis.

**Commit:** `feat(iam): blacklist access tokens on logout`

**Done when:** After logout, a call through the gateway with the old token returns 401.


### PR 3: Password and RBAC tests


#### Stage 9: Password hashing check

**Objective:** Prove passwords are stored safely.

**Steps:**

1. Confirm `SecurityConfig` uses BCrypt (strength 12) or Argon2; set it explicitly if not.
2. Extend `PasswordPolicyTest`: weak passwords rejected; hash ≠ input; two hashes of the same password differ.

**Commit:** `test(iam): verify password policy and hashing`

**Done when:** Tests pass.


#### Stage 10: RBAC integration test

**Objective:** Customers can't reach admin routes.

**Steps:**

1. `RbacIntegrationTest` (MockMvc + Testcontainers Postgres): CUSTOMER token → 403 on `/api/v1/users/**` and a sample `/api/v1/admin/*`; SYSTEM_ADMIN → 200.

**Commit:** `test(iam): add RBAC integration tests`

**Done when:** Test passes in `mvn ... -pl auth-iam-service -am verify`.


### PR 4: Google OAuth 2.0


#### Stage 11: OAuth client config

**Objective:** Prepare Google sign-in.

**Steps:**

1. Add `spring-boot-starter-oauth2-client`; env `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` in `application.yml`, `.env.example`, compose, k8s.
2. Create the OAuth client in Google Cloud Console with redirect `http://localhost:8080/api/v1/auth/oauth2/google/callback`.

**Commit:** `build(iam): add Google OAuth client configuration`

**Done when:** Service boots with the variables set.


#### Stage 12: Authorize endpoint

**Objective:** Start the login flow.

**Steps:**

1. `GET /api/v1/auth/oauth2/google/authorize` creates a random `state` (stored in Redis 5 min) and redirects to Google.

**Commit:** `feat(iam): add Google authorize redirect`

**Done when:** Opening the URL shows Google's consent screen.


#### Stage 13: Callback and user upsert

**Objective:** Turn a Google login into a SwiftDrop JWT.

**Steps:**

1. `GET .../callback`: check `state`, exchange `code`, read email/name, find or create a `CUSTOMER`, issue tokens.
2. Redirect to the frontend with a one-time code (`/auth/callback?code=`), then `POST /api/v1/auth/oauth2/exchange` swaps it for tokens. Never put tokens in the URL.
3. `GoogleOAuthCallbackTest` with a mocked token endpoint.

**Commit:** `feat(iam): handle Google OAuth callback and issue app JWT`

**Done when:** End to end in a browser: Google login lands logged in as a customer.


### PR 5: Frontend auth


#### Stage 14: Sign-in and sign-up pages

**Objective:** Real forms instead of the dev persona switcher.

**Steps:**

1. Validated sign-in and sign-up forms calling `authApi.ts`; show server errors safely.
2. Keep `PersonaSwitcherDevBar` only when `import.meta.env.DEV`.

**Commit:** `feat(frontend): add sign-in and sign-up pages`

**Done when:** Register then log in works through the gateway.


#### Stage 15: Continue with Google

**Objective:** One-click social login.

**Steps:**

1. Button links to the authorize URL; `/auth/callback` page exchanges the code and stores tokens.

**Commit:** `feat(frontend): add Continue with Google button`

**Done when:** Google login ends on the customer home.


#### Stage 16: Interceptors

**Objective:** Tokens attached and expiry handled automatically.

**Steps:**

1. In `apiClient.ts`: attach `Authorization: Bearer`; on 401 try one refresh then retry; if refresh fails, log out; on 403 show a 'not allowed' toast.

**Commit:** `feat(frontend): add auth interceptors with refresh`

**Done when:** Expire the access token (short TTL in dev): next call refreshes silently.


#### Stage 17: User management page

**Objective:** Admins manage users.

**Steps:**

1. `/ops/users`: list users (compact grid), change role, activate/deactivate via existing `UserRoleController` endpoints.

**Commit:** `feat(frontend): add admin user management page`

**Done when:** As SYSTEM_ADMIN you can deactivate a user and they can no longer log in.


### PR 6: Endpoints for other members


#### Stage 18: Contact lookup

**Objective:** Member 9 can find where to send notifications.

**Steps:**

1. `GET /internal/v1/users/{id}/contact` → email, phone. Internal only (gateway blocks `/internal`).

**Commit:** `feat(iam): add internal user contact lookup`

**Done when:** Calling it on port 8081 returns the contact; through the gateway returns 404.


#### Stage 19: merchant.approved consumer

**Objective:** Put `merchantId` into merchant tokens.

**Steps:**

1. Consume `merchant.approved` (key from Member 9), set `merchantId` on the user; idempotent on `eventId`.

**Commit:** `feat(iam): store merchant id from merchant approval events`

**Done when:** After approval and re-login, the merchant's JWT has `merchantId`.


### PR 7: Documentation


#### Stage 20: Diagrams and spec

**Objective:** Your assigned deliverable.

**Steps:**

1. Google OAuth sequence diagram and JWT lifecycle spec (Part D) in `docs/design/m2/`.

**Commit:** `docs(iam): add OAuth sequence and JWT lifecycle spec`

**Done when:** Files committed and shared with the team lead.

## Part C. Testing and security (summary)

Every test below is written and committed inside a stage in Part B. Use this table as a final checklist.

| Test | Type | What it proves |
|---|---|---|
| `PasswordPolicyTest` (extend) | Unit | Weak passwords rejected; hash is BCrypt/Argon2 and not equal to input; two hashes of same password differ |
| `TokenIssuanceServiceTest` (extend) | Unit | RS256 header + `kid`; expired token rejected; tampered signature rejected |
| `RbacIntegrationTest` | Integration (MockMvc + Testcontainers Postgres) | A `CUSTOMER` token gets 403 on `/api/v1/users/**` and any `/api/v1/admin/*` route; `SYSTEM_ADMIN` gets 200 |
| `LogoutBlacklistTest` | Integration (Redis Testcontainer) | Token is rejected after logout |
| `GoogleOAuthCallbackTest` | Unit (mock Google token endpoint) | New Google user is created as `CUSTOMER`; invalid `state` rejected |
| `LoginAttemptServiceTest` | Unit | Lockout after N failures |

No passwords, tokens or emails in logs.

## Part D. Documentation deliverable and demo

1. *Google OAuth 2.0 sequence diagram:* browser → frontend → gateway → auth-service → Google authorize → callback → code exchange → profile → user upsert → app JWT → frontend.
2. *JWT lifecycle spec (1–2 pages):* claims table, RS256 + JWKS, lifetimes, refresh rotation, logout blacklist, how the gateway turns claims into `X-User-*` headers.

**Your demo part:** "Continue with Google" login end to end, then show a customer token blocked from an admin route (403).

## Part E. Finishing up

**E1. Definition of done (tick every box before you call your part finished).**

- [ ] Every item in your "Gaps to close" checklist is done or has a written reason why not.
- [ ] `mvn -f backend\pom.xml -pl auth-iam-service -am verify` passes locally.
- [ ] Coverage report exists for your module (once Member 1 adds JaCoCo: `backend/auth-iam-service/target/site/jacoco/index.html`). Aim for **70%+ line coverage** on `service/` and `controller/`.
- [ ] Swagger docs for your endpoints are complete (`@Tag`, `@Operation`, `@Schema`) and `docs/api-specs` is updated.
- [ ] Frontend types regenerated with `npm run api:sync:specs` (commit only **your** service's generated file).
- [ ] `npm run lint` and `npm run build` pass in `frontend/`.
- [ ] DB and RabbitMQ credentials in `application.yml` come from env variables (no hard-coded values).
- [ ] No secrets in code, no personal data (phone, address, names) in logs or error messages.
- [ ] Your service block in `docker-compose.yml` starts and is healthy (`docker compose up -d auth-iam-service`).
- [ ] `infra/k8s/services/auth-iam-service.yml` has every env variable your `application.yml` reads (bonus marks).
- [ ] Documentation deliverable exported (PNG/PDF + source) to `docs/design/m2/` and sent to whoever compiles the final design PDF.

**E2. Test evidence for the submission.** Save `backend/auth-iam-service/target/surefire-reports/` and the JaCoCo `index.html` (as a screenshot or PDF) into `docs/test-reports/m2/`.

**E3. Demo preparation.** Rehearse your part of the demo script (Part D) twice on a clean `docker compose down -v` start. Be ready to explain your code, your diagrams, and one security decision you made.

**E4. Contribution sheet line.** Send the team lead a 3-line summary: service, frontend screens, tests and diagrams you delivered, with links to your merged PRs.
