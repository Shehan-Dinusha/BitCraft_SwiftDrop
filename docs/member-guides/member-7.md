# Member 7: Uditha · Merchant B2B Integration

> Draft guide, written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`).
> Your assignment list is the source of truth for **what** you build. The repo is the source of truth for **how** (stack, folders, ports, names).

## 0. Your scope at a glance

| Item | Value |
|---|---|
| Backend module | `backend/merchant-b2b-service` |
| Local app port | `8086` |
| Database | `merchant-postgres` container, host port `5437`, database `swiftdrop_merchant` |
| Frontend folders you own | `frontend/src/features/merchant/` except webhook files (`MerchantPortalDashboard.tsx`, `ApiKeyManagerCard.tsx`, `BulkCsvDropzone.tsx`, new `pages/MerchantRegistrationPage.tsx`), new `features/admin-ops/pages/MerchantApprovalPage.tsx`, `src/services/api/merchantApi.ts` |
| Documentation deliverable | Sequence diagram for B2B API order ingestion + merchant verification workflow diagram |
| Branch prefix | `feature/m7-<short-topic>` (off `dev`, PR back into `dev`) |

Read [README.md](README.md) in this folder once. It has the shared-file ownership map and the list of cross-member contracts.

## 1. What already exists on `dev`

- `MerchantAccountController` `/api/v1/merchants`: create, get, list.
- `ApiKeyManagementController`: create/list/revoke keys under `/api/v1/merchants/{merchantId}/api-keys`; `POST /internal/v1/api-keys/validate` (used by the gateway's `MerchantApiKeyGlobalFilter`). Keys are stored as **SHA-256 hashes**; raw key returned once.
- `MerchantOrderIngestController`: `POST /api/v1/merchants/{merchantId}/ingest/bulk`, CSV upload, batch status.
- `MerchantEventPublisher` publishes `order.created` **directly**.
- Entities `MerchantAccount` (status defaults to `ACTIVE`), `MerchantApiKey`, `BulkIngestBatch`, `BulkBatchItem`; Flyway V1, V2; six test classes.
- Frontend: `MerchantPortalDashboard`, `ApiKeyManagerCard`, `BulkCsvDropzone`, `merchantApi.ts`.

## 2. Gaps to close

- [ ] **No admin approval.** Accounts start `ACTIVE`. Add `PENDING_APPROVAL → APPROVED | REJECTED | SUSPENDED`, admin approve/reject endpoints, and block keys and orders unless `APPROVED`.
- [ ] **`/api/v1/b2b/orders` doesn't exist.** Add it (single + bulk) for API-key callers. Ask Member 1 to add `/api/v1/b2b/**` to the merchant route and to the API-key filter paths.
- [ ] **Orders bypass order-service.** Today you publish `order.created` yourself, so merchant parcels have no tracking number, receipt or history in Member 3's service. Normalise the order and call Member 3's `POST /internal/v1/orders` instead; Member 3 publishes the event. Remove your direct publish once that works.
- [ ] **Merchant identity in JWT.** The gateway forwards a `merchantId` claim, but nobody sets it. On approval publish `merchant.approved {merchantId, ownerUserId}` so Member 2 stores it on the user.
- [ ] **OAuth 2.0 client credentials** (optional; API keys satisfy "API keys **or** client credentials"). Do API keys well first.
- [ ] **Key regeneration** (rotate = create new + revoke old) and a "last used" timestamp.
- [ ] **Merchant dashboard order table** showing merchant-originated parcels and their states.

## 3. Boundaries

**You own:** `backend/merchant-b2b-service/`; `MerchantApiKeyValidationRequest/Result` DTOs in `common-contracts`; the frontend files above.

**You do not touch:** webhook settings and logs (Member 8, even though they're in the merchant portal); `MerchantApiKeyGlobalFilter` in the gateway (ask Member 1); order creation logic (Member 3).

## 4. Who you depend on

| Member | What you need | Until it arrives |
|---|---|---|
| Member 3 (Dulanga) | `POST /internal/v1/orders` (body agreed together: pickup, drop-off, parcel, `merchantId`, `externalRef`) → returns `orderId`, `trackingNumber` | Keep your current publish path behind a flag `ORDER_FORWARD_MODE=event|rest` |
| Member 1 (Shehan) | Gateway: `/api/v1/b2b/**` routed to you and protected by the API-key filter; internal paths blocked | Call your service on port 8086 directly |
| Member 2 (Nimsara) | Consumes `merchant.approved` and adds `merchantId` claim | Admin sets it manually |
| Member 9 (Umesh) | Adds `merchant.approved` routing key + `MerchantApprovedEvent` record | Declare it locally |

Others depend on you for: API-key validation (gateway), merchant ids (Member 8 webhooks), merchant order table data.

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
git checkout -b feature/m7-setup
```

**A3. Environment file.**

```powershell
Copy-Item .env.example .env
```

Never commit `.env`. Every new variable you add goes into `.env.example`, `docker-compose.yml` and `infra/k8s/services/merchant-b2b-service.yml` (rule from `AGENTS.md`).

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
docker compose up -d rabbitmq merchant-postgres
docker compose ps
```

RabbitMQ UI: http://localhost:15672 (user `swiftdrop`, password `changeme_rabbit`).

**A7. Run your service and confirm it boots.**

```powershell
mvn -f backend\pom.xml -pl merchant-b2b-service -am spring-boot:run
```

Open http://localhost:8086/actuator/health and http://localhost:8086/swagger-ui.html.

**A8. Record today's baseline.** Run your module's tests once and write down what passes and fails before you change anything:

```powershell
mvn -f backend\pom.xml -pl merchant-b2b-service -am test
```

**A9. Read before coding:** `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `docs/LOCAL-DEVELOPMENT.md`, `frontend/COMPONENTS.md`, and `.agents/rules/*.md`. They define package layout, DTO rules (Java records + `@Schema`), Flyway rules (new `V<N>__*.sql`, never edit an old one), and the Velocity design-system rules.

## Part B0. How you work every day (same for all members)

1. Start from fresh `dev`: `git checkout dev; git pull origin dev; git checkout -b feature/m7-<topic>`.
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
- Branch per PR: `feature/m7-<pr-topic>` from fresh `dev`.

> **Before you start:** `repo-fix-plan.md` (credentials, gateway URLs, compose) is being done separately. Pull `dev` once it's merged.


### PR 1: Approval workflow


#### Stage 1: Approval columns

**Objective:** Merchants start pending.

**Steps:**

1. Flyway `V3__merchant_approval.sql`: default status `PENDING_APPROVAL`; add `owner_user_id`, `business_reg_no`, `approved_by`, `approved_at`, `rejection_reason`. Update the entity and a `MerchantStatus` enum.

**Commit:** `feat(merchant): add merchant approval fields`

**Done when:** New merchants are created as PENDING_APPROVAL.


#### Stage 2: Merchant self-registration

**Objective:** Logged-in merchant registers a business.

**Steps:**

1. `POST /api/v1/merchants/register` taking business details; `owner_user_id = X-User-Id`.

**Commit:** `feat(merchant): add merchant registration endpoint`

**Done when:** Registration creates a pending merchant.


#### Stage 3: Admin approve and reject

**Objective:** Admin approval step.

**Steps:**

1. `GET /api/v1/admin/merchants?status=`, `POST /api/v1/admin/merchants/{id}/approve`, `/reject` (reason required); SYSTEM_ADMIN only.
2. Ask Member 1 to route `/api/v1/admin/merchants/**` to you.
3. `ApprovalRbacTest`.

**Commit:** `feat(merchant): add admin approval and rejection`

**Done when:** Non-admin gets 403; admin approves successfully.


#### Stage 4: Block unapproved merchants

**Objective:** Required rule.

**Steps:**

1. Key creation and order ingestion return 403 unless `APPROVED`.
2. `PendingMerchantCannotOrderTest`.

**Commit:** `feat(merchant): block keys and orders for unapproved merchants`

**Done when:** Test passes.


#### Stage 5: merchant.approved event

**Objective:** Let IAM add `merchantId` to tokens.

**Steps:**

1. Publish `merchant.approved {eventId, merchantId, ownerUserId, approvedAt}` after commit (record from Member 9; declare locally until it lands).

**Commit:** `feat(merchant): publish merchant approved event`

**Done when:** Message visible in RabbitMQ UI after approval.


### PR 2: API keys


#### Stage 6: Hashing test

**Objective:** Prove keys are stored hashed.

**Steps:**

1. `ApiKeyHashingTest`: stored ≠ raw; right key validates; one changed char fails; revoked fails. Use `MessageDigest.isEqual` for comparison.

**Commit:** `test(merchant): verify API key hashing and validation`

**Done when:** Test passes.


#### Stage 7: Regenerate and last used

**Objective:** Key management controls.

**Steps:**

1. `POST /api/v1/merchants/{id}/api-keys/{keyId}/regenerate` (create new + revoke old); store `last_used_at` on validation (Flyway V4).

**Commit:** `feat(merchant): add API key regeneration and last-used tracking`

**Done when:** Old key stops working after regenerate.


### PR 3: B2B order API


#### Stage 8: B2B single order endpoint

**Objective:** Platforms create orders with an API key.

**Steps:**

1. `POST /api/v1/b2b/orders` (header `X-API-Key`); merchant id only from gateway `X-Merchant-Id`.
2. Ask Member 1 to route `/api/v1/b2b/**` and include it in the API-key filter.
3. `ApiKeyHeaderAuthTest`: missing/invalid key → 401.

**Commit:** `feat(merchant): add B2B order creation endpoint`

**Done when:** curl with a valid key returns 201.


#### Stage 9: Normalisation

**Objective:** Clean data before forwarding.

**Steps:**

1. `MerchantOrderNormalizer`: phone format, district names to `SriLankanDistrict`, grams → kg; unit tests.

**Commit:** `feat(merchant): normalise merchant order payloads`

**Done when:** Normaliser tests pass.


#### Stage 10: Forward to order service

**Objective:** Merchant parcels get tracking numbers and receipts.

**Steps:**

1. `OrderServiceClient` calling Member 3's `POST /internal/v1/orders` (`ORDER_SERVICE_URL`); `ORDER_FORWARD_MODE=rest|event` flag (event = old direct publish) until Member 3's endpoint is merged.
2. `B2bOrderForwardingTest` with a mock client.

**Commit:** `feat(merchant): forward merchant orders to order service`

**Done when:** Response includes Member 3's tracking number.


#### Stage 11: Bulk and idempotency

**Objective:** High-throughput ingestion.

**Steps:**

1. `POST /api/v1/b2b/orders/bulk` (≤ 500) with per-item result `{externalRef, trackingNumber | error}`; idempotency on `merchantId + externalRef`.

**Commit:** `feat(merchant): add bulk B2B orders with idempotency`

**Done when:** Resending a batch creates no duplicates.


#### Stage 12: Remove direct publish

**Objective:** Order service is the only `order.created` publisher.

**Steps:**

1. When `rest` mode is proven, delete the direct `order.created` publish and the flag.

**Commit:** `refactor(merchant): remove direct order.created publishing`

**Done when:** Merchant orders still appear in dispatch's queue.


### PR 4: Frontend


#### Stage 13: Registration page

**Objective:** Merchants apply.

**Steps:**

1. `/merchant/register` business details form.

**Commit:** `feat(frontend): add merchant registration page`

**Done when:** Submitting creates a pending merchant.


#### Stage 14: Admin approval screen

**Objective:** Admins decide.

**Steps:**

1. `/ops/merchants`: pending table with approve/reject + reason (compact grid).

**Commit:** `feat(frontend): add merchant approval screen`

**Done when:** Approve from the UI.


#### Stage 15: Orders table

**Objective:** Merchant sees their parcels.

**Steps:**

1. Merchant dashboard orders table with tracking number and status badge.

**Commit:** `feat(frontend): add merchant orders table`

**Done when:** Orders created by API appear.


#### Stage 16: API key controls

**Objective:** Generate, regenerate, revoke with examples.

**Steps:**

1. `ApiKeyManagerCard`: show raw key once; copyable curl and Postman examples.

**Commit:** `feat(frontend): add API key controls with usage examples`

**Done when:** Copied curl works against the gateway.


### PR 5: Documentation


#### Stage 17: Diagrams

**Objective:** Your assigned deliverable.

**Steps:**

1. B2B ingestion sequence and merchant verification workflow (Part D) in `docs/design/m7/`.

**Commit:** `docs(merchant): add B2B ingestion and verification diagrams`

**Done when:** Files committed and shared.

## Part C. Testing and security (summary)

Every test below is written and committed inside a stage in Part B. Use this table as a final checklist.

| Test | Type | What it proves |
|---|---|---|
| `ApiKeyHashingTest` | Unit | Stored value ≠ raw key; validation succeeds with the right key, fails with a changed character; revoked key fails |
| `ApiKeyHeaderAuthTest` | MockMvc | Missing/invalid `X-API-Key` → 401 on `/api/v1/b2b/orders` |
| `PendingMerchantCannotOrderTest` | MockMvc | **Required:** `PENDING_APPROVAL`/`REJECTED` merchant → 403 on order creation and key generation |
| `ApprovalRbacTest` | MockMvc | Non-admin → 403 on approve/reject |
| `B2bOrderForwardingTest` | Unit (mock order client) | Normalised payload sent once; duplicate `externalRef` not resent |
| `BulkCsvOrderProcessorTest` (extend) | Unit | Bad rows reported, good rows accepted |

## Part D. Documentation deliverable and demo

1. *Sequence diagram, B2B API order ingestion:* e-commerce platform → gateway (API-key filter → validate) → merchant-service (normalise) → order-service (create, `order.created`) → response with tracking number.
2. *Merchant verification workflow:* register → PENDING_APPROVAL → admin approve/reject → APPROVED (keys enabled) / REJECTED.

**Your demo part:** register a merchant, approve it as admin, generate a key, run the `curl` to create an order, and show it in the merchant dashboard. (Member 8 then shows the webhook.)

## Part E. Finishing up

**E1. Definition of done (tick every box before you call your part finished).**

- [ ] Every item in your "Gaps to close" checklist is done or has a written reason why not.
- [ ] `mvn -f backend\pom.xml -pl merchant-b2b-service -am verify` passes locally.
- [ ] Coverage report exists for your module (once Member 1 adds JaCoCo: `backend/merchant-b2b-service/target/site/jacoco/index.html`). Aim for **70%+ line coverage** on `service/` and `controller/`.
- [ ] Swagger docs for your endpoints are complete (`@Tag`, `@Operation`, `@Schema`) and `docs/api-specs` is updated.
- [ ] Frontend types regenerated with `npm run api:sync:specs` (commit only **your** service's generated file).
- [ ] `npm run lint` and `npm run build` pass in `frontend/`.
- [ ] DB and RabbitMQ credentials in `application.yml` come from env variables (no hard-coded values).
- [ ] No secrets in code, no personal data (phone, address, names) in logs or error messages.
- [ ] Your service block in `docker-compose.yml` starts and is healthy (`docker compose up -d merchant-b2b-service`).
- [ ] `infra/k8s/services/merchant-b2b-service.yml` has every env variable your `application.yml` reads (bonus marks).
- [ ] Documentation deliverable exported (PNG/PDF + source) to `docs/design/m7/` and sent to whoever compiles the final design PDF.

**E2. Test evidence for the submission.** Save `backend/merchant-b2b-service/target/surefire-reports/` and the JaCoCo `index.html` (as a screenshot or PDF) into `docs/test-reports/m7/`.

**E3. Demo preparation.** Rehearse your part of the demo script (Part D) twice on a clean `docker compose down -v` start. Be ready to explain your code, your diagrams, and one security decision you made.

**E4. Contribution sheet line.** Send the team lead a 3-line summary: service, frontend screens, tests and diagrams you delivered, with links to your merged PRs.
