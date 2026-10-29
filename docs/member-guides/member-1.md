# Member 1: Shehan · API Gateway & Real-Time Tracking Hub

> Draft guide, written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`).
> Your assignment list is the source of truth for **what** you build. The repo is the source of truth for **how** (stack, folders, ports, names).

## 0. Your scope at a glance

| Item | Value |
|---|---|
| Backend module | `backend/api-gateway` |
| Local app port | `8080` |
| Database | None (stateless) |
| Frontend folders you own | `frontend/src/app/` (shell, routes table, providers except Auth), `frontend/src/design-system/` (except `AiAssistantDock`), `frontend/src/features/public-tracking/` (new), `frontend/src/features/customer/pages/LiveTrackingPage.tsx`, `features/customer/components/LiveMapCanvas.tsx`, `src/services/websocket/`, `src/services/api/trackingHubApi.ts`, `src/hooks/useDeliverySocket.ts` |
| Documentation deliverable | Gateway network architecture diagram + WebSocket/SSE handshake workflow |
| Branch prefix | `feature/m1-<short-topic>` (off `dev`, PR back into `dev`) |

Read [README.md](README.md) in this folder once. It has the shared-file ownership map and the list of cross-member contracts.

## 1. What already exists on `dev`

You wrote most of the current scaffold, so this is mainly a checklist against your assignment.

- `backend/api-gateway`: Spring Cloud Gateway (reactive). `GatewayRoutesConfig` routes all nine services, `CorsGlobalConfiguration`, `CorrelationIdFilter`, `JwtAuthenticationGlobalFilter` (strips client-sent `X-User-*` headers, verifies JWT, injects identity headers), `MerchantApiKeyGlobalFilter`, `PublicTrackingRateLimitFilter` (Redis).
- Tracking hub: `ParcelStatusEventConsumer` (queue `swiftdrop.gateway.tracking.queue`, binding `parcel.status.changed.#`), `TrackingDataSanitizerProxy`, `TrackingBroadcastHubService`, SSE (`LiveTrackingStreamController`), raw WebSocket + STOMP handlers, `PublicTrackingController` at `/api/v1/tracking/{trackingNumber}`.
- Tests: `TrackingDataSanitizerProxyTest`, `TrackingTokenVerifierTest` only.
- Root infra: `docker-compose.yml` (RabbitMQ, Redis, 9 Postgres, and only **two** app containers: `auth-iam-service`, `api-gateway`), `.env.example`, 10 Dockerfiles, `infra/k8s/*`, GitHub workflows.
- Frontend shell: `App.tsx`, `AppShell.tsx`, `routes.tsx`, providers, Velocity design system and catalog.

## 2. Gaps to close (your checklist)

Shared-platform gaps. The routing, password, RabbitMQ, IAM port and compose items are covered by `repo-fix-plan.md` (done separately by one person); the rest are in your stages:

- [ ] **Routing does not work outside Kubernetes.** Routes use `lb://<service>` but there is no service discovery. Make each route URI come from config, e.g. `${ORDER_SERVICE_URL:http://localhost:8082}`, and set `http://customer-order-service:8082` style values in compose.
- [ ] **DB password mismatch.** Compose Postgres uses `changeme_pg`; services default to `swiftdrop`. Pick one and align `.env.example`, compose, and `docs/LOCAL-DEVELOPMENT.md`.
- [ ] **RabbitMQ user mismatch.** Compose creates `swiftdrop`/`changeme_rabbit`, but 8 services set no RabbitMQ credentials (Spring falls back to `guest`) and `auth-iam-service` defaults to `guest`. Each owner fixes their own `application.yml` (their guide says so); you make sure compose passes `RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD` to every service block.
- [ ] **IAM port mismatch.** Compose maps `iam-postgres` to host `5431`, `auth-iam-service/application.yml` and the local guide say `5432`. Align with Member 2.
- [ ] **Compose is missing 8 services and the frontend.** Add a block for every backend service (copy the `auth-iam-service` pattern) and the `frontend` (its `Dockerfile` + `nginx.conf` already exist). The assignment requires frontend + all services + DB + broker in compose.
- [ ] **CI never runs.** `ci-backend.yml` triggers on `services/**`, but code lives under `backend/**`. Fix the path filters (also check `ci-frontend.yml`) and the `mvn` commands (they need `-f backend/pom.xml`).
- [ ] **No coverage tool.** Add the JaCoCo plugin once in `backend/pom.xml` (`prepare-agent` + `report` bound to `verify`) so every member gets a report.
- [ ] **Frontend route skeleton.** Add a route and an empty placeholder page for every member's screen (list in Phase 1 below) so nobody else has to edit `routes.tsx`.
- [ ] **Shell slots.** Add an empty slot in the `AppShell` header for Member 9's `<NotificationBell/>` and a floating slot for Member 10's `<AiAssistantDock/>`.

Your own feature gaps:

- [ ] **Public tracking only works from memory.** `TrackingBroadcastHubService` keeps `latestViews` in a `ConcurrentHashMap`. A tracking number with no event since the gateway started returns nothing. On a cache miss, call Member 3's order lookup (see Dependencies) and cache the sanitised result.
- [ ] **Wrong key for delivery events.** `TrackingBroadcastHubService` line ~74 uses `event.deliveryId().toString()` as the tracking number for `DeliveryStateChangedEvent`. Key every channel by `trackingNumber` only, from `ParcelStatusChangedEvent`.
- [ ] **No public tracking page.** `LiveTrackingPage` sits behind a `CUSTOMER` role guard. Build a public, mobile-first page at `/track/:trackingNumber` (no login).
- [ ] **Timeline must show** Created → Picked Up → In Transit → Out for Delivery → Delivered / Failed, re-rendered live from SSE/WebSocket without refresh.
- [ ] **Rate-limit and route-protection tests** are missing (Part C).
- [ ] **RS256.** When Member 2 switches to RS256, change `JwtAuthenticationGlobalFilter` and `TrackingTokenVerifier` to verify with the public key from the JWKS URL.

## 3. Boundaries

**You own:** everything in `backend/api-gateway/`; root `docker-compose.yml`, `.env.example`, `README.md`, `docs/LOCAL-DEVELOPMENT.md`, `.github/workflows/*`, `backend/pom.xml` (parent), `infra/k8s/base/` and ingress; `common-contracts/dto/*` (`ApiResponse`, `ApiErrorResponse`, `PagedResult`); the frontend shell, routes table, design system and the folders listed above.

**You do not touch:** other services' code; `common-contracts/events/*` and `infra/local/rabbitmq/definitions.json` (Member 9); `AuthProvider`, `useAuthStore`, `apiClient.ts`, `RoleGuard` (Member 2); any persona feature folder except the tracking files above.

**Shared files you are the gatekeeper for:** other members will send you small PRs for their compose block, gateway route paths and env variables. Review them quickly; they are blocked until you merge.

## 4. Who you depend on

| Member | What you need | Until it arrives |
|---|---|---|
| Member 2 (Nimsara) | RS256 public key / JWKS URL and final claim names | Keep HS256 verification; put the algorithm behind a config switch `JWT_ALGORITHM` |
| Member 3 (Dulanga) | An internal lookup `GET /internal/v1/orders/tracking/{trackingNumber}` returning status history (no PII needed beyond district/city) | Use the existing public `/api/v1/orders/tracking/{trackingNumber}` directly |
| Member 6 (Kasun) | `DeliveryState`/`PublicOrderStatus` with `OUT_FOR_DELIVERY` and a public `FAILED` | Render unknown statuses as a neutral step |
| Member 9 (Umesh) | Final `ParcelStatusChangedEvent` schema | Use the current record |

Others depend on you for: compose blocks, gateway routes, route placeholders, shell slots, JaCoCo, CI. Do these in your first week.

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
git checkout -b feature/m1-setup
```

**A3. Environment file.**

```powershell
Copy-Item .env.example .env
```

Never commit `.env`. Every new variable you add goes into `.env.example`, `docker-compose.yml` and `infra/k8s/services/api-gateway.yml` (rule from `AGENTS.md`).

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
docker compose up -d rabbitmq redis
docker compose ps
```

RabbitMQ UI: http://localhost:15672 (user `swiftdrop`, password `changeme_rabbit`).

**A7. Run your service and confirm it boots.**

```powershell
mvn -f backend\pom.xml -pl api-gateway -am spring-boot:run
```

Open http://localhost:8080/actuator/health and http://localhost:8080/swagger-ui.html.

**A8. Record today's baseline.** Run your module's tests once and write down what passes and fails before you change anything:

```powershell
mvn -f backend\pom.xml -pl api-gateway -am test
```

**A9. Read before coding:** `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `docs/LOCAL-DEVELOPMENT.md`, `frontend/COMPONENTS.md`, and `.agents/rules/*.md`. They define package layout, DTO rules (Java records + `@Schema`), Flyway rules (new `V<N>__*.sql`, never edit an old one), and the Velocity design-system rules.

> The gateway needs RabbitMQ and Redis. To test routing to a service, also start that service; until route URIs are config-driven (your Phase 1), call services on their own ports.

## Part B0. How you work every day (same for all members)

1. Start from fresh `dev`: `git checkout dev; git pull origin dev; git checkout -b feature/m1-<topic>`.
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
- Branch per PR: `feature/m1-<pr-topic>` from fresh `dev`.

> **Before you start:** the baseline fixes in `repo-fix-plan.md` (credentials, config-driven gateway routes, compose with all services + frontend, `OUT_FOR_DELIVERY`) are being done separately by one person. Don't redo them; pull `dev` once they are merged. Your stages start after that.


### PR 1: Frontend shell for the whole team (do this first, others wait on it)


#### Stage 1: Route placeholders

**Objective:** Give every member a route and an empty page so nobody else edits `routes.tsx`.

**Steps:**

1. Create one placeholder component per screen, e.g. `features/customer/pages/OrderHistoryPage.tsx` returning `<h1>Order history</h1>`.
2. Add routes: `/track/:trackingNumber` (public), `/customer/book`, `/customer/orders`, `/ops/zones`, `/ops/dispatch`, `/courier`, `/courier/delivery/:id`, `/merchant/register`, `/merchant`, `/ops/merchants`, `/merchant/webhooks`, `/ops/notifications`, `/ops/analytics`, `/ops/users`.
3. Keep the existing `RoleGuard` usage; only add entries.

**Commit:** `feat(frontend): add route placeholders for all member screens`

**Done when:** `npm run lint` and `npm run build` pass; every URL opens its placeholder after login with the right role.


#### Stage 2: Header and floating slots

**Objective:** Let Member 9 and Member 10 plug in components without editing `AppShell`.

**Steps:**

1. In `AppShell.tsx`, render `<NotificationBellSlot/>` in the header and `<AiAssistantSlot/>` fixed bottom-right.
2. Each slot imports from a stub file (`features/notifications/NotificationBell.tsx`, `design-system/components/AiAssistantDock/index.ts`) that returns `null` for now.
3. Post the route table and slot file names in the team chat.

**Commit:** `feat(frontend): add notification and assistant slots to app shell`

**Done when:** Shell renders unchanged; stubs exist at the agreed paths.


### PR 2: Gateway hardening


#### Stage 3: CORS from environment

**Objective:** Stop allowing every origin.

**Steps:**

1. Read `CORS_ALLOWED_ORIGINS` (comma list, default `http://localhost:5173`) in `CorsGlobalConfiguration`.
2. Allow credentials; allow methods GET/POST/PUT/PATCH/DELETE/OPTIONS.
3. Add the variable to `.env.example`, compose and `infra/k8s/services/api-gateway.yml`.

**Commit:** `feat(gateway): configure CORS origins from environment`

**Done when:** Preflight from `http://localhost:5173` succeeds; from `http://evil.test` it is rejected (check with curl `-H Origin:`).


#### Stage 4: Identity-header stripping test

**Objective:** Prove clients can't forge identity headers.

**Steps:**

1. Add `JwtAuthenticationGlobalFilterTest` (WebTestClient + stub downstream).
2. Cases: no token on a protected path → 401; forged `X-User-Role: SYSTEM_ADMIN` with a customer token → downstream sees `CUSTOMER`; forged header on a public path → removed; expired token → 401.

**Commit:** `test(gateway): cover JWT filter and identity header stripping`

**Done when:** `mvn -f backend\pom.xml -pl api-gateway -am test` passes with the new tests.


#### Stage 5: Block internal routes

**Objective:** No browser may reach `/internal/**` endpoints of any service.

**Steps:**

1. Add a high-priority global filter that returns 404 for any path containing `/internal/`.
2. Add `InternalRouteBlockedTest`.

**Commit:** `feat(gateway): block internal service paths at the edge`

**Done when:** `curl http://localhost:8080/internal/v1/api-keys/validate` returns 404; test passes.


#### Stage 6: Security response headers

**Objective:** Add standard browser protections.

**Steps:**

1. Add a filter setting `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`.
2. Assert them in a small WebTestClient test.

**Commit:** `feat(gateway): add security response headers`

**Done when:** Headers visible in any gateway response; test passes.


#### Stage 7: Review public paths

**Objective:** Make sure only intended paths skip JWT.

**Steps:**

1. In `JwtAuthenticationGlobalFilter.PUBLIC_PATHS`, keep auth login/register/refresh, tracking, docs and health.
2. Move merchant API paths into the API-key filter's list only (they must require a key, not be fully public).
3. Write the final list in the class Javadoc.

**Commit:** `fix(gateway): restrict public paths to auth, tracking and docs`

**Done when:** Calling `/api/v1/merchant-order-ingest/...` without a key now returns 401; tests pass.


### PR 3: Tracking hub backend


#### Stage 8: Key channels by tracking number

**Objective:** Fix the bug where delivery events are keyed by `deliveryId`.

**Steps:**

1. In `TrackingBroadcastHubService`, remove the `DeliveryStateChangedEvent` path that uses `deliveryId().toString()` as the tracking number.
2. Use only `ParcelStatusChangedEvent.trackingNumber` as the channel key.
3. Update `TrackingBroadcastHubServiceTest` (create it if missing): an event reaches only subscribers of its tracking number.

**Commit:** `fix(gateway): key tracking channels by tracking number`

**Done when:** Unit test passes; publishing a test event in RabbitMQ UI updates only that tracking number's stream.


#### Stage 9: Order lookup client

**Objective:** Prepare a client for cache misses.

**Steps:**

1. Add `OrderTrackingClient` (WebClient, base URL from the order route URL, 2 s timeout).
2. Call `GET /internal/v1/orders/tracking/{trackingNumber}` (Member 3); until it exists use `/api/v1/orders/tracking/{trackingNumber}`.
3. Map 404 to `Optional.empty()`; unit test with MockWebServer.

**Commit:** `feat(gateway): add order tracking lookup client`

**Done when:** Client test passes for 200, 404 and timeout.


#### Stage 10: Cache-miss fallback

**Objective:** Public tracking works for parcels with no event since startup.

**Steps:**

1. In `PublicTrackingController`, if `latestViews` has nothing, call `OrderTrackingClient`, sanitise with `TrackingDataSanitizerProxy`, store in the cache, return it.
2. Return 404 `ApiErrorResponse` when the order service says not found.

**Commit:** `feat(gateway): fall back to order service on tracking cache miss`

**Done when:** Restart the gateway, request an existing tracking number: returns data instead of 404.


#### Stage 11: Stronger sanitiser

**Objective:** No personal data in public tracking.

**Steps:**

1. Extend `TrackingDataSanitizerProxyTest`: phones masked (`07X***5678`), recipient name reduced to initials, no address lines, only city/district.

**Commit:** `test(gateway): extend PII sanitiser coverage`

**Done when:** Tests pass; fix the sanitiser in the same commit if any case fails.


#### Stage 12: Heartbeats and stream end

**Objective:** Keep connections alive and close them cleanly.

**Steps:**

1. SSE: emit a comment heartbeat every 15 s; complete the stream when status is terminal (DELIVERED, FAILED, CANCELLED, RETURNED_TO_SENDER).
2. WebSocket: same heartbeat as a ping message.

**Commit:** `feat(gateway): add tracking stream heartbeats and terminal close`

**Done when:** `curl -N http://localhost:8080/api/v1/tracking/<id>/stream` shows a heartbeat every 15 s and closes after a DELIVERED event.


#### Stage 13: Rate-limit response and tests

**Objective:** Stop tracking-number scraping.

**Steps:**

1. Make `PublicTrackingRateLimitFilter` return 429 with `Retry-After`.
2. Add `PublicTrackingRateLimitFilterTest`: 61st request in the window from one IP → 429, a second IP → 200.
3. Add `TrackingScrapingTest`: 100 sequential guessed numbers are throttled.

**Commit:** `test(gateway): verify public tracking rate limiting`

**Done when:** Tests pass; a quick PowerShell loop of 70 calls shows 429s.


### PR 4: Public tracking page


#### Stage 14: Tracking API client

**Objective:** One typed client for tracking calls.

**Steps:**

1. In `trackingHubApi.ts` add `getPublicTracking(trackingNumber)` using generated gateway types (`npm run api:sync:specs`).

**Commit:** `feat(frontend): add public tracking API client`

**Done when:** `npm run lint` passes.


#### Stage 15: Live tracking hook

**Objective:** Reusable live updates with fallbacks.

**Steps:**

1. Create `useTrackingStream(trackingNumber)`: open `EventSource` on `/api/v1/tracking/{n}/stream`; on error fall back to WebSocket `/ws/tracking?trackingId=`, then to polling every 10 s.
2. Reconnect with backoff (1, 2, 5, 10 s); close on unmount and on terminal status.

**Commit:** `feat(frontend): add live tracking stream hook`

**Done when:** In the browser network tab, the SSE connection opens, and killing the gateway triggers reconnect attempts.


#### Stage 16: Public tracking page

**Objective:** Show the timeline without login.

**Steps:**

1. `features/public-tracking/pages/PublicTrackingPage.tsx` at `/track/:trackingNumber` plus an input form at `/track`.
2. Timeline with `DeliveryProgressStepper`: Created → Picked Up → In Transit → Out for Delivery → Delivered / Failed, colours from `deliveryStateColors`.
3. Not-found and loading states.

**Commit:** `feat(frontend): add public parcel tracking page`

**Done when:** Opening `/track/<real number>` logged out shows the current status.


#### Stage 17: Mobile layout

**Objective:** Usable on a phone.

**Steps:**

1. Single column at 360 px, `tabular-nums` for the tracking ID, 56 px touch targets, no horizontal scroll.

**Commit:** `style(frontend): make tracking page mobile friendly`

**Done when:** Chrome device toolbar at 360×740 shows no overflow.


#### Stage 18: Reuse in customer tracking

**Objective:** One implementation for both pages.

**Steps:**

1. Point the customer `LiveTrackingPage` at `useTrackingStream`; delete its duplicate socket logic if unused elsewhere.

**Commit:** `refactor(frontend): reuse tracking hook in customer tracking page`

**Done when:** Both pages update live when Member 6 changes a status.


### PR 5: RS256 verification (after Member 2 ships JWKS)


#### Stage 19: Algorithm switch

**Objective:** Support RS256 without breaking HS256 during transition.

**Steps:**

1. Add `JWT_ALGORITHM` (default `HS256`) and `JWT_JWKS_URL`.
2. When RS256: load the public key from JWKS (cache 10 min) in `JwtAuthenticationGlobalFilter` and `TrackingTokenVerifier`.
3. Tests for both modes.

**Commit:** `feat(gateway): verify RS256 tokens via JWKS`

**Done when:** With `JWT_ALGORITHM=RS256`, a token from Member 2's service is accepted; a token signed with another key is rejected.


### PR 6: Coverage and CI (deferred; do when Dula says)


#### Stage 20: JaCoCo

**Objective:** Measurable coverage for every module.

**Steps:**

1. Add `jacoco-maven-plugin` to `backend/pom.xml` build plugins: `prepare-agent` and `report` bound to `verify`.

**Commit:** `build: add JaCoCo coverage reports`

**Done when:** `mvn -f backend\pom.xml verify` creates `target/site/jacoco/index.html` in each module.


#### Stage 21: CI paths

**Objective:** Make workflows actually run.

**Steps:**

1. Change `services/**` to `backend/**` in `ci-backend.yml`; use `mvn -f backend/pom.xml`; check `ci-frontend.yml` paths.

**Commit:** `ci: fix workflow paths for backend and frontend`

**Done when:** A PR touching `backend/` triggers the backend workflow.


### PR 7: Kubernetes bonus (optional)


#### Stage 22: Ingress and config

**Objective:** Deploy the stack on Kubernetes.

**Steps:**

1. Ingress routes `/api`, `/ws` to the gateway and `/` to the frontend.
2. ConfigMap + Secret per service; check each member's manifest has their env vars.

**Commit:** `feat(k8s): add ingress and per-service config`

**Done when:** `kubectl apply -k` (or `-f`) on a local cluster brings all pods to Ready.


### PR 8: Documentation


#### Stage 23: Diagrams

**Objective:** Your assigned deliverable.

**Steps:**

1. Gateway network architecture diagram and WebSocket/SSE handshake sequence (details in Part D), saved to `docs/design/m1/`.

**Commit:** `docs(gateway): add network architecture and handshake diagrams`

**Done when:** PNG + source files committed; team lead has them for the design PDF.

## Part C. Testing and security (summary)

Every test below is written and committed inside a stage in Part B. Use this table as a final checklist.

| Test | Type | What it proves |
|---|---|---|
| `PublicTrackingRateLimitFilterTest` | Unit (WebTestClient) | The 61st request in 60 s from one IP gets `429`; another IP is unaffected |
| `TrackingScrapingTest` | Integration | Sequential guessing of tracking numbers is throttled |
| `InternalRouteBlockedTest` | Unit | `/internal/v1/**` returns 403/404 through the gateway |
| `JwtAuthenticationGlobalFilterTest` | Unit | No token → 401 on protected path; forged `X-User-Role` header is stripped; expired token → 401 |
| `TrackingDataSanitizerProxyTest` (extend) | Unit | Phone numbers masked, recipient name reduced, no address lines |
| `TrackingBroadcastHubServiceTest` | Unit | An event reaches only subscribers of that tracking number |

Run: `mvn -f backend\pom.xml -pl api-gateway -am verify`.

## Part D. Documentation deliverable and demo

**Diagrams (save under `docs/design/m1/`):**

1. *Gateway network architecture:* browser → (Ingress) → api-gateway → 9 services; Redis (rate limit, JWT blacklist), RabbitMQ tracking queue; mark public vs protected vs internal paths.
2. *WebSocket/SSE handshake workflow:* sequence of client connect → gateway upgrade → subscribe to tracking number → event from RabbitMQ → sanitise → push; include heartbeat, reconnect and the polling fallback.

**Your demo part:** open `/track/SD-XXXX-XXXX` on a phone-sized window, have Member 6 move the parcel one step, and show the timeline update without refresh. Then show a `429` from rapid requests.

## Part E. Finishing up

**E1. Definition of done (tick every box before you call your part finished).**

- [ ] Every item in your "Gaps to close" checklist is done or has a written reason why not.
- [ ] `mvn -f backend\pom.xml -pl api-gateway -am verify` passes locally.
- [ ] Coverage report exists for your module (once Member 1 adds JaCoCo: `backend/api-gateway/target/site/jacoco/index.html`). Aim for **70%+ line coverage** on `service/` and `controller/`.
- [ ] Swagger docs for your endpoints are complete (`@Tag`, `@Operation`, `@Schema`) and `docs/api-specs` is updated.
- [ ] Frontend types regenerated with `npm run api:sync:specs` (commit only **your** service's generated file).
- [ ] `npm run lint` and `npm run build` pass in `frontend/`.
- [ ] DB and RabbitMQ credentials in `application.yml` come from env variables (no hard-coded values).
- [ ] No secrets in code, no personal data (phone, address, names) in logs or error messages.
- [ ] Your service block in `docker-compose.yml` starts and is healthy (`docker compose up -d api-gateway`).
- [ ] `infra/k8s/services/api-gateway.yml` has every env variable your `application.yml` reads (bonus marks).
- [ ] Documentation deliverable exported (PNG/PDF + source) to `docs/design/m1/` and sent to whoever compiles the final design PDF.

**E2. Test evidence for the submission.** Save `backend/api-gateway/target/surefire-reports/` and the JaCoCo `index.html` (as a screenshot or PDF) into `docs/test-reports/m1/`.

**E3. Demo preparation.** Rehearse your part of the demo script (Part D) twice on a clean `docker compose down -v` start. Be ready to explain your code, your diagrams, and one security decision you made.

**E4. Contribution sheet line.** Send the team lead a 3-line summary: service, frontend screens, tests and diagrams you delivered, with links to your merged PRs.
