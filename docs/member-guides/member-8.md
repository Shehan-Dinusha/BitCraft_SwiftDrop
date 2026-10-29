# Member 8: Hansana · Webhook Delivery & Resiliency

> Draft guide, written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`).
> Your assignment list is the source of truth for **what** you build. The repo is the source of truth for **how** (stack, folders, ports, names).

## 0. Your scope at a glance

| Item | Value |
|---|---|
| Backend module | `backend/webhook-resiliency-service` |
| Local app port | `8087` |
| Database | `webhook-postgres` container, host port `5438`, database `swiftdrop_webhook` |
| Frontend folders you own | `frontend/src/features/merchant/pages/WebhookResiliencyConsolePage.tsx`, `features/merchant/components/WebhookDlqReplayTable.tsx`, new `features/merchant/components/WebhookSettingsCard.tsx`, `src/services/api/webhookApi.ts` |
| Documentation deliverable | Resilient integration pattern architecture diagram (Broker → Consumer → Retry loop → DLQ) |
| Branch prefix | `feature/m8-<short-topic>` (off `dev`, PR back into `dev`) |

Read [README.md](README.md) in this folder once. It has the shared-file ownership map and the list of cross-member contracts.

## 1. What already exists on `dev`

- `WebhookSubscriptionController` `/api/v1/webhooks/subscriptions`: create, get, list, status, delete, `POST /{id}/test` (sends `webhook.test.ping`), `GET /{id}/attempts`.
- `DlqReplayController` `/api/v1/webhooks/dlq`: list, get, replay one, replay all.
- `DeliveryStateWebhookConsumer` on `webhook.events.queue` (Java binds `parcel.status.changed.#`, `order.created`, `webhook.dispatch.requested`).
- `HmacSignatureGenerator`, `ExponentialBackoffScheduler` (base 2 s, cap 30 s), `OutboundWebhookDispatcher`, `DlqReplayProcessor`, Resilience4j config, WebClient.
- Entities `WebhookEndpoint`, `WebhookDeliveryAttempt`, `DeadLetterWebhookRecord`; Flyway V1, V2; six test classes.
- Frontend: `WebhookResiliencyConsolePage`, `WebhookDlqReplayTable`, `webhookApi.ts`.

## 2. Gaps to close

- [ ] **Retry is fake.** `OutboundWebhookDispatcher` loops in one thread and sleeps at most 500 ms (`Thread.sleep(Math.min(delay, 500L))`). Your list requires attempts at **0 s, 30 s, 2 min, 10 min**, then DLQ. Make retries durable (survive a restart): store `next_attempt_at` and poll with `@Scheduled`, or use RabbitMQ retry queues with per-message TTL that dead-letter back to the work queue.
- [ ] **Broker config drift.** `definitions.json` binds `webhook.events.queue` to `webhook.#` only, but your Java config binds `parcel.status.changed.#`, `order.created`, `webhook.dispatch.requested`. Ask Member 9 to make `definitions.json` match.
- [ ] **Which merchant gets the webhook?** `ParcelStatusChangedEvent` has no `merchantId`. Your queue already receives `order.created` (which has `merchantId` and `trackingNumber`), so keep a local table `order_merchant_map(order_id, tracking_number, merchant_id)` filled from `order.created`. No contract change needed.
- [ ] **Signing secret per merchant:** generated on subscription, shown once, stored encrypted or hashed-plus-retrievable (you need the raw secret to sign; encrypt with a key from env `WEBHOOK_SECRET_KEY`).
- [ ] **Headers:** `X-SwiftDrop-Signature: sha256=<hex>`, `X-SwiftDrop-Timestamp`, `X-SwiftDrop-Event-Id` so merchants can verify and de-duplicate.
- [ ] **Ownership:** a merchant may only see and manage their own endpoints and logs (`X-Merchant-Id`).
- [ ] **SSRF guard:** only `https://` (allow `http://localhost` in dev), no private IP ranges in production.
- [ ] **Mock merchant receiver** for the demo (can fail on demand).

## 3. Boundaries

**You own:** `backend/webhook-resiliency-service/`; `WebhookDispatchRequestedEvent` content (record file owned by Member 9); the webhook UI files above.

**You do not touch:** merchant registration, API keys, merchant dashboard (Member 7); `definitions.json` (Member 9).

## 4. Who you depend on

| Member | What you need | Until it arrives |
|---|---|---|
| Member 3 (Dulanga) | `order.created` with `merchantId` (already there) | Publish a test message |
| Member 6 (Kasun) | `parcel.status.changed` with `trackingNumber` | Test messages |
| Member 7 (Uditha) | Approved merchants have `merchantId` in JWT (via Member 2) | Take `merchantId` from a dev header |
| Member 9 (Umesh) | `definitions.json` aligned with your bindings | Your `RabbitMQConfig` declares them at startup anyway |

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
git checkout -b feature/m8-setup
```

**A3. Environment file.**

```powershell
Copy-Item .env.example .env
```

Never commit `.env`. Every new variable you add goes into `.env.example`, `docker-compose.yml` and `infra/k8s/services/webhook-resiliency-service.yml` (rule from `AGENTS.md`).

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
docker compose up -d rabbitmq webhook-postgres
docker compose ps
```

RabbitMQ UI: http://localhost:15672 (user `swiftdrop`, password `changeme_rabbit`).

**A7. Run your service and confirm it boots.**

```powershell
mvn -f backend\pom.xml -pl webhook-resiliency-service -am spring-boot:run
```

Open http://localhost:8087/actuator/health and http://localhost:8087/swagger-ui.html.

**A8. Record today's baseline.** Run your module's tests once and write down what passes and fails before you change anything:

```powershell
mvn -f backend\pom.xml -pl webhook-resiliency-service -am test
```

**A9. Read before coding:** `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `docs/LOCAL-DEVELOPMENT.md`, `frontend/COMPONENTS.md`, and `.agents/rules/*.md`. They define package layout, DTO rules (Java records + `@Schema`), Flyway rules (new `V<N>__*.sql`, never edit an old one), and the Velocity design-system rules.

## Part B0. How you work every day (same for all members)

1. Start from fresh `dev`: `git checkout dev; git pull origin dev; git checkout -b feature/m8-<topic>`.
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
- Branch per PR: `feature/m8-<pr-topic>` from fresh `dev`.

> **Before you start:** `repo-fix-plan.md` (credentials, gateway URLs, compose) is being done separately. Pull `dev` once it's merged. Retries were marked 'okay for now' by Dula for the repo baseline, but they are your assignment, so PR 1 is still yours.


### PR 1: Durable retry


#### Stage 1: Retry schedule columns

**Objective:** Store retry state in the DB.

**Steps:**

1. Flyway `V3__retry_schedule.sql`: `next_attempt_at`, `attempt_count`, `last_status_code`, `last_error`, `delivery_status` (`PENDING, SUCCEEDED, RETRYING, DEAD`) on the delivery job/attempt table; update entities.

**Commit:** `feat(webhook): add retry schedule columns`

**Done when:** Flyway applies V3; service boots.


#### Stage 2: Configurable backoff

**Objective:** 0 s, 30 s, 2 min, 10 min.

**Steps:**

1. Replace `ExponentialBackoffScheduler`'s formula with a list from `WEBHOOK_RETRY_DELAYS=0,30,120,600` (seconds); max attempts = list size.
2. Update `ExponentialBackoffSchedulerTest`.

**Commit:** `feat(webhook): configure retry delays from environment`

**Done when:** Test asserts 0/30/120/600.


#### Stage 3: Consumer creates jobs

**Objective:** Stop sending inside the listener.

**Steps:**

1. `DeliveryStateWebhookConsumer` creates one PENDING job per matching endpoint (attempt 1 due now) and returns; idempotent on `eventId`.

**Commit:** `refactor(webhook): enqueue delivery jobs from consumer`

**Done when:** A status event creates job rows.


#### Stage 4: Scheduled sender

**Objective:** Durable retries that survive restarts.

**Steps:**

1. `@Scheduled(fixedDelay = 5000)` worker selects due jobs `FOR UPDATE SKIP LOCKED`, sends, records attempt + response code, sets next attempt or SUCCEEDED.
2. Delete the `Thread.sleep` loop in `OutboundWebhookDispatcher`.

**Commit:** `feat(webhook): send due webhook jobs from scheduler`

**Done when:** Restarting the service mid-retry continues the schedule.


#### Stage 5: Retry rules and DLQ

**Objective:** Know when to give up.

**Steps:**

1. 2xx success; 408/429/5xx/timeouts retry; other 4xx straight to DLQ; after last attempt write `dead_letter_webhook_records` and publish to `dlq.webhook.events`.
2. `NonRetryable4xxTest`.

**Commit:** `feat(webhook): move exhausted deliveries to dead letter queue`

**Done when:** Test passes.


#### Stage 6: Retry integration tests

**Objective:** Required resilience tests.

**Steps:**

1. `WebhookRetryToDlqIT` (Testcontainers + MockWebServer always 500, delays in ms via env): 4 attempts then one DLQ record.
2. `WebhookRecoversIT`: 500, 500, 200 → success on attempt 3.

**Commit:** `test(webhook): verify retry backoff and DLQ fallback`

**Done when:** Both ITs pass.


### PR 2: Routing and signing


#### Stage 7: Order to merchant map

**Objective:** Know which merchant owns a parcel.

**Steps:**

1. Flyway V4 `order_merchant_map(order_id PK, tracking_number, merchant_id)`; fill from `order.created` (already bound); status events look it up.

**Commit:** `feat(webhook): map orders to merchants from order events`

**Done when:** Status event for a merchant order creates a job for that merchant only.


#### Stage 8: Encrypted signing secrets

**Objective:** Per-merchant HMAC secret.

**Steps:**

1. Generate a secret on subscription, return it once, store AES-GCM encrypted with `WEBHOOK_SECRET_KEY` (env).

**Commit:** `feat(webhook): store encrypted per-endpoint signing secrets`

**Done when:** DB column holds ciphertext; API shows the secret only on create.


#### Stage 9: Signature headers

**Objective:** Merchants can verify and dedupe.

**Steps:**

1. Sign `timestamp + "." + body` with HMAC-SHA-256; send `X-SwiftDrop-Signature: sha256=<hex>`, `X-SwiftDrop-Timestamp`, `X-SwiftDrop-Event-Id`.
2. Extend `HmacSignatureGeneratorTest` with a fixed test vector and a tampered body.

**Commit:** `feat(webhook): sign outgoing webhooks with HMAC SHA-256`

**Done when:** Test vector matches.


#### Stage 10: SSRF guard

**Objective:** Don't call internal addresses.

**Steps:**

1. Reject non-https URLs (allow `http://localhost` in dev) and private/link-local IPs; `SsrfGuardTest`.

**Commit:** `feat(webhook): validate subscription URLs against SSRF`

**Done when:** Test passes.


### PR 3: Merchant endpoints


#### Stage 11: Ownership checks

**Objective:** Merchants only see their own webhooks.

**Steps:**

1. All subscription, attempts and DLQ endpoints filter by `X-Merchant-Id`; admin bypass.
2. `WebhookOwnershipTest`.

**Commit:** `feat(webhook): scope webhook endpoints to the merchant`

**Done when:** Merchant A gets 403 on B's endpoint.


#### Stage 12: Test event through the pipeline

**Objective:** Test button uses real signing and retry.

**Steps:**

1. `POST /{id}/test` enqueues a signed `webhook.test.ping` job instead of sending directly.

**Commit:** `refactor(webhook): route test pings through delivery jobs`

**Done when:** Ping appears in logs with status code.


#### Stage 13: Logs endpoint

**Objective:** Data for the logs table.

**Steps:**

1. `GET /{id}/attempts` returns status code, payload preview (tracking number + status only), retry count, delivery status, next attempt.

**Commit:** `feat(webhook): expose delivery log details`

**Done when:** Response has all fields.


#### Stage 14: Mock merchant receiver

**Objective:** Demo failures on demand.

**Steps:**

1. Under the `dev` profile only, `/dev/mock-merchant/webhook` that fails the first N calls (`MOCK_FAIL_COUNT`) then returns 200 and logs the signature check.

**Commit:** `feat(webhook): add dev mock merchant receiver`

**Done when:** Subscribing it shows 500, 500, 200 in logs.


### PR 4: Frontend


#### Stage 15: Subscription settings

**Objective:** Merchant sets URL and tests it.

**Steps:**

1. `WebhookSettingsCard`: URL input, save, show secret once, 'Send test event' button.

**Commit:** `feat(frontend): add webhook subscription settings`

**Done when:** Test event appears in logs.


#### Stage 16: Logs table

**Objective:** See every attempt.

**Steps:**

1. Logs table with status code badge (200/500), retry count, delivery status, next attempt time; payload preview in a drawer.

**Commit:** `feat(frontend): add webhook delivery logs table`

**Done when:** Retries visible live (poll 10 s).


#### Stage 17: DLQ replay

**Objective:** Recover failed deliveries.

**Steps:**

1. Use `WebhookDlqReplayTable` with replay one / replay all.

**Commit:** `feat(frontend): wire DLQ replay actions`

**Done when:** Replay creates a new job.


### PR 5: Documentation


#### Stage 18: Diagram

**Objective:** Your assigned deliverable.

**Steps:**

1. Resilient integration diagram (Broker → Consumer → Job table → Scheduler → Retry → DLQ) in `docs/design/m8/`.

**Commit:** `docs(webhook): add resilient integration pattern diagram`

**Done when:** Files committed and shared.

## Part C. Testing and security (summary)

Every test below is written and committed inside a stage in Part B. Use this table as a final checklist.

| Test | Type | What it proves |
|---|---|---|
| `WebhookRetryToDlqIT` | Integration (Testcontainers Postgres + RabbitMQ + WireMock/MockWebServer returning 500) | **Required:** 4 attempts at the configured (shortened) delays, then one DLQ record; response codes logged |
| `WebhookRecoversIT` | Integration | 500, 500, 200 → success on attempt 3, no DLQ |
| `HmacSignatureGeneratorTest` (extend) | Unit | **Required:** known key + body → known hex (test vector); tampered body fails verify |
| `NonRetryable4xxTest` | Unit | 400 → DLQ immediately |
| `WebhookOwnershipTest` | MockMvc | Merchant A → 403 on Merchant B's endpoint and logs |
| `SsrfGuardTest` | Unit | `http://169.254.169.254` and `http://10.0.0.1` rejected |

## Part D. Documentation deliverable and demo

*Resilient integration pattern diagram:* `swiftdrop.events.exchange` → `webhook.events.queue` → consumer → delivery job table → scheduler → merchant endpoint → (fail) retry 30 s / 2 min / 10 min → DLQ → manual replay. Show the HMAC header and the idempotency key.

**Your demo part:** set the mock receiver to fail twice, trigger a status change (Member 6), and show attempts with 500, 500, 200 in the logs table; then make it always fail and show the DLQ entry and a replay.

## Part E. Finishing up

**E1. Definition of done (tick every box before you call your part finished).**

- [ ] Every item in your "Gaps to close" checklist is done or has a written reason why not.
- [ ] `mvn -f backend\pom.xml -pl webhook-resiliency-service -am verify` passes locally.
- [ ] Coverage report exists for your module (once Member 1 adds JaCoCo: `backend/webhook-resiliency-service/target/site/jacoco/index.html`). Aim for **70%+ line coverage** on `service/` and `controller/`.
- [ ] Swagger docs for your endpoints are complete (`@Tag`, `@Operation`, `@Schema`) and `docs/api-specs` is updated.
- [ ] Frontend types regenerated with `npm run api:sync:specs` (commit only **your** service's generated file).
- [ ] `npm run lint` and `npm run build` pass in `frontend/`.
- [ ] DB and RabbitMQ credentials in `application.yml` come from env variables (no hard-coded values).
- [ ] No secrets in code, no personal data (phone, address, names) in logs or error messages.
- [ ] Your service block in `docker-compose.yml` starts and is healthy (`docker compose up -d webhook-resiliency-service`).
- [ ] `infra/k8s/services/webhook-resiliency-service.yml` has every env variable your `application.yml` reads (bonus marks).
- [ ] Documentation deliverable exported (PNG/PDF + source) to `docs/design/m8/` and sent to whoever compiles the final design PDF.

**E2. Test evidence for the submission.** Save `backend/webhook-resiliency-service/target/surefire-reports/` and the JaCoCo `index.html` (as a screenshot or PDF) into `docs/test-reports/m8/`.

**E3. Demo preparation.** Rehearse your part of the demo script (Part D) twice on a clean `docker compose down -v` start. Be ready to explain your code, your diagrams, and one security decision you made.

**E4. Contribution sheet line.** Send the team lead a 3-line summary: service, frontend screens, tests and diagrams you delivered, with links to your merged PRs.
