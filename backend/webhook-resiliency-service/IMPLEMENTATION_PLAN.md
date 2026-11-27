# Webhook Delivery & Resiliency Service — Implementation Plan (Member 8)

This is the working reference for all Member 8 implementation work. It records what exists, what is
missing, the agreed order of work, cross-member dependencies, and how to run and verify things locally.

- **Service path:** `backend/webhook-resiliency-service`. Below, `WH/` means `src/main/java/com/swiftdrop/webhook/`.
- **Shared contracts path:** `CC/` means `backend/common-contracts/src/main/java/com/swiftdrop/contracts/`.
- **Status:** written on 2026-11-14, after the technical audit and the "plan + dev-mode" setup task (Step 0).
  **Re-verified on 2026-11-27** against `dev` at `33c56175`, after the team's PRs #87–#205: see
  [Section 2a](#2a-verified-state-on-2026-11-27) and the re-ordered [remaining work](#remaining-work-re-ordered-2026-11-27).
- **Local testing:** [`docs/LOCAL-TESTING.md`](docs/LOCAL-TESTING.md) (synthetic events and the real parcel lifecycle,
  every public status).

---

## MANDATORY INSTRUCTIONS FOR FUTURE CLAUDE CLI TASKS

Follow these on **every** task that touches this service:

1. **Before every major implementation task**, read this `IMPLEMENTATION_PLAN.md` again.
2. Read the project instruction files: `CLAUDE.md`, `AGENTS.md`, `backend/AGENTS.md`, `.agents/rules/*`, and `frontend/AGENTS.md` for UI work. Inspect the current implementation **before** changing anything. Those files take precedence over this plan.
3. Never assume a planned feature is still missing because it appears in the roadmap. Check the code and the [Progress log](#progress-log) for whether an earlier step already implemented it.
4. If something is already implemented correctly, preserve it and move on.
5. If something is partially implemented, complete or improve it. Do not replace working code unnecessarily.
6. If the real implementation differs from this plan, investigate why before changing it. The code may be right and the plan wrong.
7. If a roadmap step is no longer necessary, recommend removing it and explain why. Do not silently skip it or silently do it.
8. If you discover a new problem or dependency, update this document (roadmap, risks or coordination table).
9. Never silently change shared event contracts (`common-contracts`), RabbitMQ topology (`RabbitMQConfig` / `infra/local/rabbitmq/definitions.json`), or another member's service. These need coordination (see [Section 6](#6-cross-member-dependencies)).
10. Do not introduce unrelated refactoring.
11. Do not implement several unrelated roadmap steps in one task. Keep each task focused on the requested step. Large steps are already split.
12. After completing a step, update this guide (the step's **Status** line and the [Progress log](#progress-log)) to reflect the real state. Never mark unfinished work as completed.
13. Do not introduce new infrastructure (Kafka, Redis, ShedLock, Resilience4j, etc.) unless a real requirement is documented here first. The intended stack is Spring Boot + PostgreSQL + RabbitMQ.
14. Every task ends with the report described in [Section 3](#3-mandatory-report-after-every-implementation-task).

### Automated tests (current rule, since 2026-11-27)

The 2026-11-14 "no automated tests" decision below is **superseded**. The team restored and extended the tests
(PR #186), and the repository rules now apply:

- `AGENTS.md`: `mvn test` runs unit tests only; `mvn -f backend/pom.xml -pl webhook-resiliency-service -am verify`
  (Testcontainers, Docker required) is expected before any PR touching persistence, Flyway or messaging, and CI runs it.
- `backend/AGENTS.md`: a new Flyway migration counts as proven only when `verify` applies it.
- JaCoCo `check` runs in `verify`. This module has a ratchet of **85 % line / 68 % branch** (`pom.xml`; the project
  default is 70 % / 50 %).
- Every implementation step therefore names the tests it adds or changes, and runs the module's `verify`.
  Manual verification (see [`docs/LOCAL-TESTING.md`](docs/LOCAL-TESTING.md)) is still required on top.
- How to run `verify` without a host JDK 25: `docs/LOCAL-TESTING.md`, section 6.
- The failsafe `failIfNoTests=false` override mentioned below was removed by PR #186.

### Superseded: NO AUTOMATED TESTS IN THIS DEVELOPMENT SESSION (2026-11-14 – 2026-11-22)

On 2026-11-14 the service owner decided the following:

- **Removed:** all test sources under `backend/webhook-resiliency-service/src/test` were deleted (see Step 0).
- **Do not create tests.** No unit, integration, MVC, HTTP, Testcontainers or end-to-end tests are to be written in any later step of this development session, and no roadmap step includes test work.
- **Do instead:** verify by compilation (`-DskipTests`), static reading of the change, and the **manual verification** listed in each step.
- **Never claim tested.** Do not say something was "tested" when it was only compiled. Say "compiled" or "manually verified (how)".
- **Lost coverage.** The deleted tests covered ingestion deduplication, merchant resolution, the status-before-`order.created` race, ingestion DLQ behaviour, broker/`definitions.json` parity and schema constraints. That regression protection is gone, so [Section 1](#1-preserve-these-working-components) components must be changed with extra care. They are best not changed at all.
- **Shared test infrastructure is untouched.** Test dependencies in `pom.xml` (Testcontainers etc.) were left in place. They are harmless with no test sources.
- **CI consequence (fixed 2026-11-15):** this module's `pom.xml` had failsafe `<failIfNoTests>true</failIfNoTests>` (a guard so its ITs could never be silently skipped). With the test sources gone, CI's `mvn verify` failed with "No tests to run!". It is now `false`, with a comment. **When *IT classes are restored, set it back to `true`.**

---

## 1. Preserve these working components

These are verified as working, and the design depends on them. Do not rewrite them. Change them only when a step explicitly requires it, and then minimally.

| Component | Where | Why it is correct |
|---|---|---|
| RabbitMQ topology | `WH/config/RabbitMQConfig`, `infra/local/rabbitmq/definitions.json` (owner: Member 9) | `webhook.events.queue` (quorum, TTL 24h, DLX → `dlq.webhook.events.queue`); bindings `order.created`, `parcel.status.changed.#`, `webhook.dispatch.requested`. Java and `definitions.json` match. Re-declaring with different args causes `PRECONDITION_FAILED`. |
| Listener container | `RabbitMQConfig.webhookIngestionListenerContainerFactory` | AUTO ack after the listener returns, i.e. after the DB commit. Bounded stateless retry, then reject to the DLQ. Ignores `__TypeId__`. |
| Consumer | `WH/messaging/consumer/InboundEventConsumer` | Thin. MDC with eventId, correlationId and eventType. No HTTP. |
| Decoder | `WH/messaging/decoder/InboundEventDecoder` | Type comes from the routing key. Canonical UUIDs required. Schema version header checked. `customerId` stripped. |
| Ingestion | `WH/service/ingestion/InboundEventIngestionService` | One transaction: per-order advisory lock, inbox insert with `ON CONFLICT DO NOTHING`, merchant resolution, parking of early status events, release on `order.created`, first-claim-wins ownership. |
| Fan-out | `WH/service/ingestion/DeliveryFanOut` | One `webhook_deliveries` row per (event, active matching endpoint of **that** merchant). |
| SQL | `WH/domain/repository/IngestionJdbcRepository` | Advisory lock, idempotent inserts. |
| Schema V1–V3 | `src/main/resources/db/migration/V1..V3` | Composite FKs enforce tenant isolation. Lease, schedule and replay columns already exist. **Never edit an applied migration.** Add `V4+`. |
| Access rules | `WH/service/access/MerchantAccess` | Merchant sees only its own data. Another merchant's resource returns 404. SYSTEM_ADMIN sees all. |
| Caller identity | `WH/config/CallerContextArgumentResolver` + `CC/security/CallerContext` | Identity comes from gateway-injected headers only. |
| API paths | `/api/v1/webhooks/subscriptions/**`, `/api/v1/webhooks/deliveries/failed/**` (PR #179); legacy `/api/v1/webhooks/dlq/**` | The first two are used by the frontend (`frontend/src/services/api/webhookApi.ts`); all are routed by the gateway (`/api/v1/webhooks/**`). No screen uses `/dlq`; since Step 17 it is read-only history (MERCHANT/SYSTEM_ADMIN) and its replay answers 410. |
| Subscription patterns | `WH/service/subscription/EventSubscriptionPatterns`, `WebhookEventType` | Validated on registration, lenient on read. |

---

## 2. Verified current state (as of Step 0)

### Works
- **Ingestion:** events are consumed, deduplicated, resolved to the owning merchant and turned into `PENDING` rows in `webhook_deliveries`.
- **Endpoint management:** endpoint CRUD, enable/disable, test ping, legacy attempt list, legacy DLQ list and replay. The frontend console uses the real API.

### Missing or wrong
Verified in code; the audit IDs are in brackets.

| ID | Problem | Where | Severity |
|---|---|---|---|
| C1 | **No delivery worker.** Nothing reads `webhook_deliveries`, so jobs stay `PENDING` forever and merchants receive nothing for real parcel events. | — | Critical |
| C2 | **SSRF.** `targetUrl` accepts any URL (`@URL` only). Test ping returns the first 250 characters of the response body, and attempts expose `responseBody`. This lets a merchant read internal services. | `dto/CreateWebhookEndpointRequest`, `WebhookSubscriptionService.testPing`, `OutboundWebhookDispatcher.dispatch` | Critical |
| C3 | A customer can set any `merchantId` on their own order. **Upstream:** Member 3. | customer-order `OrderController.createOrder` → `CustomerOrderService.persistNewOrder` | Critical (cross-member) |
| H1 | **Secrets.** Signing secrets are stored in plaintext and returned on every GET and list, including an admin list-all. There is no rotation. | `WebhookSubscriptionService.mapToResponse`, `webhook_endpoints.secret_key` | High |
| H2 | **No merchant order reference.** Nothing in any event carries `externalRef`. delivery-execution also generates a different tracking number. | `CC/events/order/OrderCreatedEvent` | High (cross-member) |
| H3 | **Unsafe legacy replay.** Replay does HTTP inside `@Transactional`, has no concurrency guard, and every failed replay inserts a *new* DLQ row. Failed test pings also create DLQ rows. | `DlqReplayService`, `OutboundWebhookDispatcher` | High |
| H4 | **Weak signature.** The HMAC covers the body only, so the timestamp is not protected. `X-SwiftDrop-Delivery-Id` is random per attempt, and there is no event-id header. | `OutboundWebhookDispatcher.dispatch` | High |
| H5 | **Wrong HTTP handling.** Every failure is recorded as HTTP 500. 3xx is probably treated as success (unverified). `WebClient` has no connect timeout. Retry is an in-thread sleep capped at 500 ms. | `OutboundWebhookDispatcher`, `config/WebClientConfig`, `ExponentialBackoffScheduler` | High |
| M5 | Target URLs are logged, and raw exception text is stored in error fields. | `WebhookSubscriptionService.createEndpoint`, `OutboundWebhookDispatcher`, `DlqReplayService` | Medium |
| M-obs | No metrics: actuator exposes `health,info` only. | `application.yml` | Medium |
| UI-1 | The create-endpoint error is not visible. On failure, `handleCreateEndpoint` sets the page-level banner but leaves the Register modal open, and the banner renders behind it. The merchant sees only the static help text (observed by the owner with `http://example.com/webhook`; the API correctly returned 400 "Target URL must use HTTPS"). Fix: show the error inside the modal. **Status: ✅ DONE, browser-verified by the owner (2026-11-15).**
  - New `createError` state. On failure it is shown as a `StatusBanner` (danger, "Registration failed", dismissible) at the top of the still-open Register form instead of the page banner.
  - It is cleared on resubmit, Cancel and dialog close (new `closeCreateModal`).
  - The success path and the one-time secret dialog are unchanged. | `WebhookResiliencyConsolePage.tsx` (`handleCreateEndpoint`, Register modal) | Low |

Also note:
- `OutboundWebhookDispatcher` is `@Deprecated`. Only test ping and legacy replay use it.
- `webhook.dispatch.requested` is bound but has no producer. The consumer deliberately rejects it to the DLQ.
- The old alias paths `/api/v1/dlq-replay` and `/api/v1/webhook-subscription` were removed (the gateway never routed them); use `/api/v1/webhooks/dlq` and `/api/v1/webhooks/subscriptions`.

## 2a. Verified state on 2026-11-27

Section 2 describes the state at Step 0. This is the state after Steps 1–10 and the team's PRs, checked in the code.

**Unchanged since Step 10 (PR #79):** `WebhookUrlPolicy`, `BlockingAddressResolverGroup`, `WebhookHttpSender`,
`WebhookSigner`/`WebhookHeaders`, `WebhookPayloadFactory` (one equivalent Jackson call, #88), `DeliveryRetryPolicy`,
`DeliveryJobRepository`, `DeliveryAttemptRecorder`, `DeliveryProcessor`, `DeliveryFanOut`; `DeliveryWorker` and
`LeaseRecoveryJob` changed in comments only.

**Changed or added by the team:**

| PR | Effect on this service |
|---|---|
| #87 | Non-root image, ZGC; `aks` profile has no DB/RabbitMQ password defaults; Helm `readOnlyRootFilesystem` with a writable `/tmp`; legacy HMAC verify is constant-time; first unit tests and the JaCoCo gate. |
| #122 | `LogSanitizer` in `GlobalExceptionHandler`. |
| #152 | OpenAPI server `/` with a global bearer requirement; alias paths removed. |
| #179 | **Step 11** done (`TestPingService`), **Step 13** core done (`FailedDeliveryService`/`Repository`/`Controller`, `V4__one_active_replay.sql`), **Step 14** mostly done (Failed events table, event checkboxes, merchant API guide). The console is `/merchant/webhooks`, MERCHANT-only; `/ops/webhooks` is gone. |
| #183 | `codAmount` removed from `OrderCreatedEvent`; dimensions added. Outbound payload unchanged. |
| #186 | Tests restored and extended (17 classes; see `docs/LOCAL-TESTING.md` §6); module coverage ratchet 85 %/68 %; `failIfNoTests` override removed. |
| #190 | Docker build stage JDK 26 compiling `release 25`; runtime JRE 25; CI JDK 25. |
| #203 | Decoder: `merchantId` is the owner only when `customerId` equals it or is absent. Ingestion: `parcel.status.changed` with `previousPublicStatus == newPublicStatus` is dropped as `NO_CHANGE` before the inbox (no row, no job). Upstream, customer bookings no longer carry `merchantId` (C3 fixed). |

**Parcel lifecycle.** The README's lifecycle section is stale: the code has 16 delivery states and 11 public
statuses (`DELIVERY_ATTEMPTED`, `RETURNING` added; `RETURNED_TO_SENDER` comes from the real `RETURNED` state). The
full mapping, who emits each event and how to trigger each one are in `docs/LOCAL-TESTING.md` §1.

**New findings (2026-11-27):**

| ID | Finding | Evidence | Owner |
|---|---|---|---|
| N1 | **Order-service cancellations never become webhooks.** `OrderEventPublisher.publishOrderCancelled` sends `previousPublicStatus = CANCELLED` (read after the order was set to CANCELLED), so the #203 `NO_CHANGE` rule drops it. Delivery's `order.cancelled` handler publishes nothing. Only a delivery-API cancel (`ASSIGNED → CANCELLED`) reaches merchants. | Source; webhook side reproduced locally (synthetic `CANCELLED → CANCELLED` → `outcome=NO_CHANGE`) | Member 3 (send the status before the change). Webhook side: add a regression test once fixed. |
| N2 | `PICKUP_ASSIGNED` is published twice for a first-mile assignment (dispatch `ManualOverrideService.publishAssignmentEvents` and delivery `handleCourierAssigned`), with different event ids, so merchants get two webhooks. | Source (was "unverified" in Section 6); not yet observed at runtime | Members 5 + 6 |
| N3 | **Resolved in R1 (2026-11-27): the guide now tells merchants to compare `occurredAt`.** Was: **Replay breaks the API guide's ordering promise.** A replay keeps its original's `occurred_at`; FIFO (D7) does not let a `DEAD` or `SUCCEEDED` job block later ones, so a replayed status normally arrives after newer ones. The merchant API guide says "Events of one parcel arrive in order". | Source (`claimDue` SQL, `queueReplays`, `ApiGuidePage.tsx`) | Member 8: correct the guide text (tell merchants to compare `occurredAt`); optionally warn in the Failed events table |
| N4 | **Documented in R1 (2026-11-27).** Hub, held-at-hub and return steps inside one public status are dropped as `NO_CHANGE`, by design. A status can legitimately go "backwards" (`OUT_FOR_DELIVERY → IN_TRANSIT` when a courier brings a parcel back; `DELIVERY_ATTEMPTED → IN_TRANSIT` on retry). | Source | Document for merchants (API guide) |
| N5 | `NO_CHANGE` trusts the producer's `previousPublicStatus`. All current producers derive it from the stored state, except N1. A missing `previousPublicStatus` is never treated as unchanged. | Source | Covered by N1 plus tests |
| N6 | **Resolved by Step 17 (2026-11-27).** Was: the legacy `/api/v1/webhooks/dlq` API is still routed: replay still signs the old way (body-only HMAC, millisecond timestamp, random Delivery-Id), makes HTTP calls inside `@Transactional`, and the list still admits DISPATCHER. Nothing writes new legacy records any more (only a failed legacy replay does), and the local table is empty. | Source; local DB count 0; hosted count unknown | Member 8: Step 17 |
| N7 | Test Ping is sent even when the endpoint is paused. Probably intended (an explicit action), not documented. | Source | Decide; document |
| N8 | The local broker may carry a stale `webhook.#` binding from old code (#25). It is not in the repository and is harmless. | Local broker | — |

---

## 3. Mandatory report after every implementation task

Every implementation task must end with a report containing **all** of the following. "Implemented successfully" on its own is not acceptable. The report must let another engineer or AI assistant understand the exact repository state without re-investigating.

1. **Objective completed:** what the step was supposed to achieve.
2. **Actual changes:** what was implemented and how it works.
3. **Files modified:** every modified file and why.
4. **Files created:** every new file and its purpose.
5. **Files deleted:** every deleted file and why.
6. **Existing functionality preserved:** important components deliberately left unchanged.
7. **Architecture changes:** data flow, database behaviour, messaging, APIs, configuration or security.
8. **Configuration changes:** new properties, environment variables, profiles and Docker changes.
9. **Dependencies:** any other service, member, shared contract or environment variable needed.
10. **Compilation/build result:** the exact commands run and their actual results.
11. **Runtime instructions:** the exact commands to start, stop and access the affected services.
12. **Manual verification:** specific actions to perform and what success looks like.
13. **Known limitations:** anything incomplete or temporarily simplified.
14. **Remaining issues:** bugs, risks or decisions that need attention.
15. **Next recommended step:** the exact next roadmap step, plus anything that must be done or agreed first.

---

## 4. Delivery decisions (Step 4: decided 2026-11-15)

Decided by the service owner on 2026-11-15 after the Step 4 review. Later steps implement them **in the step named**, not earlier. Change a decision here first, never silently in code.

**Status overview:**
- **APPROVED:** D1–D5, D7–D11, D13.
- **PROVISIONAL (pending Member 7):** D6.
- **IMPLEMENTED:** D12.

| # | Decision | Status | Decided choice | Notes / implemented in |
|---|---|---|---|---|
| D1 | Retry schedule and terminal failure | **APPROVED** | Attempt 1 immediately, then retries after 30 s, 2 min and 10 min (4 attempts in total), each delay with ±20 % random jitter. After the last failure the job becomes **`DEAD` in `webhook_deliveries`**. Outbound failures are **not** written to the legacy `dead_letter_webhook_records` table and **not** published to the RabbitMQ DLQ. | `webhook_deliveries` is the authoritative outbound delivery history and replay source; the RabbitMQ DLQ stays for inbound ingestion failures. This consciously deviates from member-guide Stage 5 ("write the legacy table and publish to `dlq.webhook.events`"). Implemented in Steps 8–10. |
| D2 | Retry-After | **APPROVED** | Honoured for HTTP 429 and 503 (seconds or HTTP-date), capped at 10 min, never shortening the normal schedule. | Step 8. |
| D3 | HTTP classification | **APPROVED** | **Success:** 2xx. **Retryable:** timeout, connection failure, 408, 425, 429, 5xx. **Permanent (straight to DEAD):** 3xx (`INVALID_RESPONSE`), other 4xx (`HTTP_CLIENT_ERROR`), blocked destination (`BLOCKED_DESTINATION`). | The guide covers 2xx/408/429/5xx/timeouts/4xx; the additions were approved. `BLOCKED_DESTINATION` is already used since Step 3. **Approved with Step 8 (2026-11-15):** `UNKNOWN` failures are retryable, within the same 4-attempt limit. Steps 7–8. |
| D4 | Signature | **APPROVED** | `X-SwiftDrop-Signature: sha256=<hex HMAC-SHA256(secret, "<timestamp>.<raw body bytes>")>`. `X-SwiftDrop-Timestamp` = Unix **seconds**. Merchants should reject requests older than **5 minutes**. | The `sha256=` prefix follows the member guide; the earlier `v1=` proposal was dropped. Step 6. |
| D5 | Request headers | **APPROVED** | `X-SwiftDrop-Signature`, `X-SwiftDrop-Timestamp`, `X-SwiftDrop-Event-Id` (inbound event id), `X-SwiftDrop-Delivery-Id` (**stable across all retries of the same job**), `X-SwiftDrop-Event` (event type), `X-SwiftDrop-Attempt`, `User-Agent`. | Steps 6–7 and 10. The legacy dispatcher's per-attempt random Delivery-Id goes away with it. |
| D6 | Payload v1 and money fields | **PROVISIONAL (pending Member 7)** | Envelope `{"id","type","apiVersion":1,"occurredAt","data"}`. `parcel.status.changed` data: `orderId`, `merchantOrderReference` (null until Step 18), `trackingNumber` (from `order_merchant_map`), `status`, `previousStatus`, `failureReason`. `order.created` data: `orderId`, `merchantOrderReference` (null), `trackingNumber`, `speed`. **All monetary fields are excluded:** `codAmount`, `declaredValue`, `estimatedPrice`, `currency`, and any pricing or payout data. Never included: `customerId`, `correlationId`, `internalSubStatus`, districts, merchant id, database ids. | Member 7 could not be reached. Approved money fields can be added later as additive properties in a documented revision. Field facts: `codAmount` = cash to collect (LKR); `declaredValue` = declared merchandise value (LKR); `estimatedPrice` = creation-time `estimated_fee` (not the final fee, not updated on revision); `currency` default `"LKR"`. The question for Member 7 is under Step 4. **Implemented in Step 5.** **2026-11-27:** `codAmount` no longer exists anywhere in the event contract (PR #183 removed cash on delivery); `declaredValue`, `estimatedPrice` and `currency` are still in `OrderCreatedEvent` and still excluded. |
| D7 | Ordering | **APPROVED** | FIFO per (endpoint, order). A later event waits while an earlier one for the same endpoint and order is still active (`PENDING`/`RETRY`/`IN_FLIGHT`). `DEAD` and `SUCCEEDED` do not block. Different parcels never block each other. | Needs the V4 columns `order_id`/`occurred_at`. Steps 9–10. |
| D8 | Disabled endpoint | **APPROVED (requires a later code change)** | Disabling an endpoint **pauses** delivery. Events arriving while it is disabled must still be **queued as `PENDING` deliveries** and become deliverable once it is re-enabled. No event is permanently skipped because of a pause. | **Gap in the current code:** `DeliveryFanOut.createDeliveries` uses `findByMerchantIdAndActiveTrue`, so no job is created for a disabled endpoint. Fix in **Step 9**: fan-out must include inactive endpoints (one narrow ingestion change), and the claim query must skip jobs of inactive endpoints. Deleted endpoints are unaffected (deletion with jobs is already blocked by the FK, 409). |
| D9 | Worker concurrency | **APPROVED** (initial, conservative) | Per replica: **max 4** concurrent requests, **max 1** per endpoint, claim batch **10**, poll every **2 s**, lease **60 s**. All configurable (`webhook.delivery.*`). | With the Helm HPA at 2–8 replicas, the cluster-wide maximum is **8–32** concurrent requests. Steps 8 (properties) and 10. |
| D10 | Replay | **APPROVED** | Replay inserts a **new** `webhook_deliveries` row (`replay_of_delivery_id` = original), keeps the **original event id**, gets a **new delivery id**, and is sent asynchronously by the worker. The API returns **202**. Only **one active replay per original** (partial unique index). | `webhook_deliveries` is the replay source (see D1). Steps 9 (index) and 13. |
| D11 | Secrets | **APPROVED** | Shown once at creation (done in Step 2) and once on rotation. Rotation **replaces** the secret immediately; **no grace period** initially. Stored AES-GCM encrypted with env `WEBHOOK_SECRET_KEY`. | **Dependency:** Member 9 must agree the `WEBHOOK_SECRET_KEY` env var across compose, `.env.example` and Helm **before Step 15 only**. It does not block Steps 5–14. Step 15. |
| D12 | SSRF dev exception | **IMPLEMENTED (Step 3)** | Exact `host:port` allowlist (`webhook.security.dev-http-allowlist`, env `WEBHOOK_SECURITY_DEV_HTTP_ALLOWLIST`). Locally only `host.docker.internal:9090`, via a gitignored `docker-compose.override.yml`. Off by default; startup fails if set under the `aks` profile. | Do not change. |
| D13 | `order.created` merchant-facing | **APPROVED** | `order.created` stays a merchant-facing, subscribable event. | Its payload follows D6 (no monetary fields). |

---

## 5. Revised roadmap

**Ordering principle.** Close the security holes that are exploitable *today* (Steps 1–3), then build the pipeline in small, reviewable pieces (5–10), then the management surface (11–14), then the remaining production hardening (15–17). Cross-member work (Step 18) runs in parallel as soon as the other member delivers.

**Production readiness.** Before any production-like use, these are required: **1, 2, 3, 7 (connect-time guard), 15 and 16**. Everything marked *(optional)* is not.

**Common manual-verification toolkit.** Every step's "Manual check" uses the toolkit in [Section 7](#7-local-development-and-manual-verification): dev-mode frontend, direct API calls, synthetic RabbitMQ events, `psql`, and a mock merchant receiver.

### Step 0 — Plan, test removal, local dev mode ✅ DONE (2026-11-14)
- **Done:** created this plan, deleted the service's test sources, and added a frontend-only local dev mode (see Section 7.2). Fixed the stale README broker binding.
- **Not changed:** no backend production code.

### Step 1 — Harden test ping and logging (small, security)
- **Objective:** remove the SSRF read-back channel and the DLQ pollution that exist today.
- **Why:** C2 (response body returned to the caller), H3 (failed pings create replayable DLQ rows), and M5 (URLs logged).
- **Inspect / modify:**
  - `WebhookSubscriptionService.testPing`, `getDeliveryAttempts`, `createEndpoint` (log line)
  - `OutboundWebhookDispatcher.dispatch`
  - `DlqReplayService.replay` (log line)
  - `dto/WebhookDeliveryAttemptResponse`
  - The frontend attempts modal in `WebhookResiliencyConsolePage.tsx` (if it renders `responseBody`)
- **Behaviour after:**
  - Ping makes **one** attempt and records it with a status code, an `error_class` and the latency.
  - It never writes `dead_letter_webhook_records`.
  - API responses no longer contain the remote response body. Either drop the field or always return null; if the DTO changes, update the OpenAPI spec `docs/api-specs/webhook-resiliency-service.yaml` and regenerate frontend types.
  - Logs and stored errors contain no URL and no raw exception message, only a sanitised class.
- **New files:** none expected.
- **Preserve:** the endpoint paths, `MerchantAccess`, and everything in ingestion.
- **Depends on:** nothing. **Other members:** none.
- **Done when:**
  - Compiled.
  - A ping to a failing URL produces no DLQ row.
  - No API returns the remote body.
  - `grep targetUrl` finds no log statements.
- **Manual check:**
  - Register an endpoint pointing at the mock receiver's `/boom` (500) and ping it.
  - `SELECT count(*) FROM dead_letter_webhook_records;` is unchanged.
  - The ping response and `GET …/attempts` contain no body text.
- **Status:** ✅ DONE (2026-11-15). As implemented:
  - **Ping.** `OutboundWebhookDispatcher.ping()` makes one attempt, no retry, no DLQ.
  - **Single attempt path.** `dispatch()`, used only by legacy replay, keeps 3 attempts plus a DLQ row. Both use one `sendOnce()`:
    - `exchangeToMono`: the real status code is recorded, and the body is released unread.
    - The status is `null` when no HTTP response arrived.
    - Classification uses exception *types* only.
    - Each attempt stores its latency.
  - **Errors.**
    - Stored `error_message` is a fixed sentence from `DlqFailureClassifier.describe()`.
    - New DLQ `failure_reason` uses the strict token format `DlqFailureClassifier.storedReason()` (e.g. `HTTP_SERVER_ERROR HTTP 500`).
  - **API.**
    - `WebhookDeliveryAttemptResponse` lost `responseBody` and gained `errorClass` and `latencyMs`.
    - `AttemptResponseMapper` regenerates `errorMessage` from the classification, so older rows with raw text or bodies are sanitised on read.
    - The OpenAPI spec and generated types are updated.
  - **Frontend.** The ping badge and attempts modal show the status or the error class, plus latency.
  - **Logs.** Target URLs were removed from the `createEndpoint` and `DlqReplayService` logs.
  - **Side effect on legacy replay.** A 3xx response now counts as a failure (`INVALID_RESPONSE`) instead of a success.
  - **Not changed.** The `response_body` DB column and old rows were left as they are (no migration). Nothing reads them any more.

### Step 2 — Stop returning signing secrets (small, security)
- **Objective:** show the secret only in the create response.
- **Why:** H1. Today every GET or list response, including an admin's list of all merchants, returns every secret.
- **Inspect / modify:**
  - `WebhookSubscriptionService.mapToResponse` and `createEndpoint`
  - `dto/WebhookEndpointResponse`, or a new create-only response DTO
  - `WebhookSubscriptionController` (`@Operation` docs)
  - The OpenAPI spec, then `npm run api:sync:specs`
  - Frontend `WebhookResiliencyConsolePage.tsx` (secret reveal/copy, which becomes a "shown once" notice) and `webhookApi.ts`
- **New files:** possibly `dto/WebhookEndpointCreatedResponse.java`.
- **Preserve:** the storage format. Encryption is Step 15.
- **Depends on:** nothing. **Other members:** none.
- **Done when:**
  - Compiled.
  - `npm run lint && npm run build` pass.
  - GET and list responses contain no secret. The create response still does.
- **Manual check:** create an endpoint in the dev UI and see the secret once. Reload the page: the secret is no longer visible. `GET /api/v1/webhooks/subscriptions` has no `secretKey`.
- **Status:** ✅ DONE (2026-11-15). As implemented:
  - **Shared response.** `secretKey` was removed from `WebhookEndpointResponse`, which create's `endpoint`, GET, list and both status toggles all use.
  - **Create response.** New `dto/WebhookEndpointCreatedResponse { endpoint, signingSecret }`. `POST /api/v1/webhooks/subscriptions` returns it, and it is the only response carrying the secret.
  - **Spec and types.** The OpenAPI spec adds `WebhookEndpointCreatedResponse` and `ApiResponseWebhookEndpointCreatedResponse`, and the webhook generated types were regenerated.
  - **Frontend.**
    - The per-row reveal and copy controls are gone.
    - After creation, a one-time "Copy your signing secret" dialog appears, with a warning and a Copy button.
    - The secret is held only in React state and cleared when the dialog closes; it is not persisted.
  - **Unchanged.** Storage stays as is: plaintext `webhook_endpoints.secret_key`, same `whsec_` format, same HMAC use. Encryption and rotation remain Step 15.
  - **Compatibility.** Existing endpoints keep their secrets for signing, but they can no longer be viewed. A merchant who lost theirs needs rotation (Step 15) or must re-create the endpoint.

### Step 3 — SSRF URL policy at registration (security)
- **Objective:** reject unsafe target URLs when an endpoint is created. Apply the same check before any legacy outbound call (ping or replay), so old stored URLs are covered too.
- **Why:** C2.
- **Rules:**
  - `https` only. Allow `http` only for hosts on the dev allowlist (D12).
  - No userinfo.
  - No IP literals in loopback, private, link-local, CGNAT, multicast, unspecified, ULA or IPv4-mapped ranges. Handle decimal and octal IP spellings by parsing with `InetAddress` *only for literals*.
  - Block the host names `localhost`, `*.localhost`, `*.svc`, `*.cluster.local`, `*.internal` (except the dev allowlist), and bare single-label names such as `auth-iam-service`.
  - Resolve the host. Reject it if any resolved address is in a blocked range. This is the registration-time check only; the connect-time check is Step 7.
- **Inspect / modify:**
  - `dto/CreateWebhookEndpointRequest`
  - `WebhookSubscriptionService.createEndpoint` and `testPing`
  - `DlqReplayService.replay`
  - `application.yml` (`webhook.security.*`)
  - `GlobalExceptionHandler` (a 400 with a generic message)
- **New files:** `WH/security/WebhookUrlPolicy.java`, `WH/security/WebhookSecurityProperties.java` (`@ConfigurationProperties`).
- **Configuration:**
  - `webhook.security.dev-allowed-hosts` (default empty), env var `WEBHOOK_SECURITY_DEV_ALLOWED_HOSTS`.
  - For local work with the service in Docker, the value `host.docker.internal,localhost` must reach the container. Pick **one** way and record it here:
    - an env line in `docker-compose.yml`, which is shared infrastructure and must be mirrored in `.env.example` and Helm per `backend/AGENTS.md`, **or**
    - an untracked `docker-compose.override.yml`.
- **Preserve:** existing stored endpoints. Do not delete or modify rows; they are checked at send time instead.
- **Depends on:** nothing. **Other members:** the infra owner if compose, `.env.example` or Helm change.
- **Done when:**
  - Compiled.
  - Bad URLs get a 400 at creation.
  - A ping to a stored internal URL is refused with `BLOCKED_DESTINATION` and makes no request.
- **Manual check:** try creating endpoints for `http://auth-iam-service:8081/x`, `https://127.0.0.1/x`, `https://169.254.169.254/x`, `https://10.0.0.5/x` and `ftp://x`. All should return 400. `https://example.com/hook` should succeed. A dev-allowlisted `http://host.docker.internal:9090/ok` should succeed.
- **Status:** ✅ DONE (2026-11-15). As implemented:
  - **Policy.** New package `WH/security/` with `WebhookUrlPolicy`, `WebhookSecurityProperties` and `UnsafeWebhookUrlException` (a fixed-message `Reason` enum). `GlobalExceptionHandler` maps it to 400 "Invalid Webhook URL".
  - **Where it is applied:**
    - **Registration:** `WebhookSubscriptionService.createEndpoint`.
    - **Every attempt:** `OutboundWebhookDispatcher.sendOnce` re-checks the stored URL. If refused, it records `BLOCKED_DESTINATION`, sends nothing, does not retry and writes no DLQ row. This covers both test ping and legacy replay.
    - **Legacy replay:** `DlqReplayService.replay` also pre-checks, returning FAILED/400 with the policy message and writing no attempt rows. The record stays unreplayed.
    - **Updates:** no endpoint-URL update operation exists (only the status toggle), so there was nothing else to protect.
  - **Dev exception.** `webhook.security.dev-http-allowlist` takes exact `host:port` entries (env `WEBHOOK_SECURITY_DEV_HTTP_ALLOWLIST`). It is empty by default, wildcards are refused, and **startup fails** if it is non-empty under the `aks` profile (verified).
    - **Decision (D12):** it is enabled locally with a personal, **gitignored** `docker-compose.override.yml` (see §7.2a). No shared compose, `.env` or Helm change was made.
  - **Existing rows** are not modified. Non-compliant ones are refused at ping and replay time.
  - **Deferred to Step 7:** the connect-time address check (DNS rebinding / TOCTOU). The HTTP client still resolves the name again when sending.

### Step 4 — Confirm delivery decisions (no code)
- **Objective:** the owner confirms or edits the decisions in [Section 4](#4-delivery-decisions-step-4-decided-2026-11-15).
- **Why:** Steps 5–10 encode these rules, and changing them later causes rework and merchant-visible contract churn.
- **Other members:** Member 7 for D6 (whether merchants should see money fields).
- **Done when:** Section 4 is marked AGREED with any edits.
- **Status:** ✅ COMPLETE WITH D6 PROVISIONAL (2026-11-15).
  - **Approved by the owner:** D1–D5, D7–D11 and D13. This resolves both conflicts with the member guide: D4 uses `sha256=`, and D1/D10 use `DEAD` only.
  - **Implemented:** D12.
  - **Provisional:** D6. All money fields are excluded until Member 7 answers, so Step 5 could proceed without assuming their answer.
  - **Still open (not blocking):** Member 7's answer to the question below. Then revisit D6 and, if fields are approved, add them as additive payload properties.
  - **D11 env var:** the Member 9 confirmation is needed **only before Step 15**. It does not block Steps 5–14.
  - **Question sent or to send to Member 7 (D6), verbatim:** "For SwiftDrop merchant webhooks, the `order.created` payload currently proposes these fields from `OrderCreatedEvent`: `speed` (delivery speed enum), `codAmount` (cash to collect on delivery, LKR), `declaredValue` (merchandise value the sender declared, LKR), `estimatedPrice` (the delivery fee estimate at order creation, i.e. `estimated_fee`, not the final fee, and not updated when the fee is later revised), and `currency` (default "LKR"). Which of these should a merchant receive in a webhook? Specifically: (1) Is it appropriate to send `codAmount` and `declaredValue` back to the merchant? (2) Should `estimatedPrice` be included, given it may differ from the final charged fee? If yes, should it be labelled as an estimate? (3) Is there any payout or pricing data that merchants must not see?"

### Step 5 — Outbound payload v1 (pure code)
- **Objective:** build deterministic public JSON bytes from a delivery's inbound event.
- **Why:** today the inbox stores the internal contract. Merchants need a stable, minimal, versioned payload without PII (D6).
- **Inspect:** `domain/entity/InboundEvent` (payload JSONB), `OrderMerchantMapping` (authoritative `tracking_number`), `InboundEventDecoder` (stored field names).
- **New files:** `WH/delivery/WebhookPayloadFactory.java`, `WH/dto/outbound/*` records.
- **Preserve:** the inbox format. Do not change what ingestion stores.
- **Depends on:** Step 4. **Other members:** none (Step 18 adds the reference later).
- **Done when:** compiled; the factory is a Spring bean with no I/O except reading passed-in entities.
- **Manual check:** temporarily log the bytes in Step 10 or call the factory from a debugger. Confirm there is no `customerId` and that the tracking number comes from the map. Keep the check light; full verification happens in Step 10.
- **Status:** ✅ DONE (2026-11-15). As implemented:
  - **Files:**
    - `WH/dto/outbound/WebhookPayloadV1` (envelope `id`, `type`, `apiVersion`=1, `occurredAt`, `data`)
    - `WH/dto/outbound/ParcelStatusChangedWebhookData`
    - `WH/dto/outbound/OrderCreatedWebhookData` (no money fields, per D6)
    - `WH/delivery/PayloadSource` (input record; `from(InboundEvent, OrderMerchantMapping)` refuses a mapping of another order or merchant)
    - `WH/delivery/WebhookPayloadFactory`: `build()` returns the DTO; `buildBytes()` returns **the exact HTTP body that Steps 6/7 must sign and send**.
  - **Determinism:** the factory is pure (no I/O, clock or randomness) and uses a private `ObjectMapper` copy that pins Java-time support, ISO dates, no indentation, `ALWAYS` inclusion and `@JsonPropertyOrder`, so nulls are explicit and field order is fixed.
  - **Timestamp:** `occurredAt` = `inbound_events.occurred_at` truncated to microseconds (the stored precision).
  - **Tracking number:** taken only from `order_merchant_map`.
  - **Status values:** the stored payload is read back through the shared contract records, so only valid enum values appear.
  - **Unwired:** nothing calls the factory yet; the worker uses it in Step 10.

### Step 6 — Signature (pure code)
- **Objective:** sign `timestamp + "." + body` and send it as `sha256=<hex>`, with the timestamp in Unix seconds (D4, approved). Provide a constant-time verify helper and the header-name constants (D5).
- **Why:** H4.
- **Inspect / modify:** `WH/resiliency/HmacSignatureGenerator` (keep it for legacy use until Step 17, or extend it).
- **New files:** `WH/delivery/WebhookSigner.java`, `WH/delivery/WebhookHeaders.java`.
- **Depends on:** Step 4.
- **Done when:** compiled; verification uses `MessageDigest.isEqual`.
- **Manual check:** in Step 10, recompute the signature from a received request in PowerShell or Python with the endpoint secret and compare (a script is in Section 7.6).
- **Status:** ✅ DONE (2026-11-15).
  - **Contract (D4):** `X-SwiftDrop-Signature: sha256=<64 lowercase hex>` = HMAC-SHA256 with key = **UTF-8 bytes of the whole stored secret string, including `whsec_`** (unchanged from the existing signer, so stored secrets keep working), over `ASCII("<timestamp>.") || raw body bytes`. `X-SwiftDrop-Timestamp` = Unix seconds. Verification window: ±5 min, symmetric (accepts |now − ts| ≤ 300 s; this is the only clock-skew allowance).
  - **Files:**
    - `WH/delivery/WebhookHeaders`: the D5 header names, `sha256=` prefix and `User-Agent` value `SwiftDrop-Webhooks/1`.
    - `WH/delivery/WebhookSigner` (`@Component`): `sign(secret, timestampSeconds, bodyBytes)`, `currentTimestampSeconds()`, and `verify(secret, timestampHeader, signatureHeader, bodyBytes)`.
      - `verify` returns a single boolean. It checks strict formats (`^sha256=[0-9a-f]{64}$`, `^[0-9]{1,12}$`), then the window, then compares with `MessageDigest.isEqual`.
      - It uses an injectable `Clock`; the default is system UTC.
      - It has no logging.
      - It is a reference/manual-verification utility only: the service sends webhooks and exposes **no** verification endpoint.
  - **Docs:** merchant verification guide added to the `info.description` of `docs/api-specs/webhook-resiliency-service.yaml`. The generated frontend types are unaffected.
  - **Unwired:** nothing calls the signer yet. It is wired in Step 7 (sender) and Step 10 (worker).
  - **Limitation:** Test Ping still uses the **legacy** scheme (body-only HMAC, millisecond timestamp, random Delivery-Id, via `HmacSignatureGenerator`) until **Step 11**. A merchant verifying a test ping with the documented contract will fail until then.

### Step 7 — HTTP sender with classification and connect-time address guard
- **Objective:** one outbound attempt returns an `AttemptOutcome(httpStatus, errorClass, latencyMs, retryAfter)`.
- **Behaviour:**
  - Connect timeout 2 s, response timeout 5 s.
  - **No redirects** (3xx means `INVALID_RESPONSE`).
  - Read at most ~1 KB of the response, and only to discard it. It is never stored.
  - A custom Reactor Netty address resolver rejects blocked IP ranges at **connect time**, which defeats DNS rebinding. It reuses the `WebhookUrlPolicy` range logic.
- **Why:** H5, and C2 defence in depth.
- **Inspect / modify:** `config/WebClientConfig` (add a dedicated webhook `WebClient` bean and leave the default one alone), `domain/enums/AttemptErrorClass`.
- **New files:**
  - `WH/delivery/WebhookHttpSender.java`
  - `WH/delivery/AttemptOutcome.java`
  - `WH/security/BlockingAddressResolverGroup.java`
- **Split if large:** 7a sender + classification; 7b connect-time resolver.
- **Depends on:** Steps 3, 4 and 6.
- **Done when:** compiled; the sender never throws for HTTP or network failures, and it always returns an outcome.
- **Manual check:** easiest after Step 10 with the mock receiver paths `/ok`, `/bad`, `/rate`, `/boom`, `/slow` and `/redirect`. Check `error_class` in `webhook_delivery_attempts`.
- **Status:** ✅ DONE (2026-11-15). As implemented:
  - **Interface for Step 10:** `WebhookHttpSender.send(WebhookRequest) → AttemptOutcome`. It never throws for HTTP, network or policy failures.
    - `WebhookRequest(targetUrl, secret, eventId, deliveryId, eventType, attempt, body)` copies the body on construction, and its `toString` hides the secret, URL and body.
    - `AttemptOutcome(httpStatus, errorClass, retryable, latencyMs, retryAfter)`:
      - `retryable` per D3.
      - `retryAfter` is the raw header (≤64 chars), captured only on 429 and 503. Step 8 parses it and applies the cap.
  - **Exact bytes:** the request's internal body array is signed with `WebhookSigner` (Step 6) and the same array is sent via `Unpooled.wrappedBuffer`, with an explicit Content-Length (no chunking). The timestamp is read once per attempt and used for both the signature and the header.
  - **Headers:** all from `WebhookHeaders` (no second convention):
    - `X-SwiftDrop-Signature`, `-Timestamp`, `-Event-Id`, `-Delivery-Id`, `-Event`, `-Attempt`
    - `User-Agent: SwiftDrop-Webhooks/1`
    - `Content-Type: application/json`, `Content-Length`
  - **Client:** a dedicated Reactor Netty `HttpClient`.
    - **Deviation from the plan text:** this is not a `WebClient` bean. `WebClient`'s exchange drains the body; `HttpClient.response()` takes the status and headers only, and the body is **never read** (0 bytes instead of "≤1 KB").
    - `newConnection()`: no pooling, so a new connection is made each time.
    - Redirects are not followed (3xx → `INVALID_RESPONSE`, Location ignored).
    - `noProxy()`, no compression.
    - Timeouts: connect 2 s, response 5 s, overall cap 10 s.
    - Response headers are limited to 16 KB.
    - TLS uses the default with hostname verification.
    - `config/WebClientConfig` and the legacy `WebClient` are untouched.
  - **SSRF:**
    - **(1) Pre-check:** `WebhookUrlPolicy.check` (Step 3) runs before signing.
    - **(2) Connect-time pinning:** the new `security/BlockingAddressResolverGroup` is the client's only resolver. It resolves each name once and fails with `BlockedDestinationException` if **any** address is blocked, so the address connected to is the address checked. It also checks already-resolved addresses and IP literals.
    - **Dev exception:** honoured only for the exact allowlisted `host:port`, via the new `WebhookUrlPolicy.isDevAllowlisted`.
    - **Outcome:** a refusal at either layer gives `BLOCKED_DESTINATION`, non-retryable, and nothing is sent.
  - **Logging:**
    - The sender logs ids, attempt, status, error class and latency only.
    - Reactor Netty's own `HttpClientConnect` WARN ("The connection observed an error", with the remote host, IP and raw TLS text) is set to `ERROR` in `application.yml`. This also applies to the legacy `WebClient`.
  - **Not wired yet:** nothing calls the sender until Step 10. Test Ping still uses the legacy dispatcher until Step 11.

### Step 8 — Retry policy (pure code)
- **Objective:** given an attempt number and an outcome, return a decision: `SUCCEEDED`, or `RETRY` at a computed time (schedule D1, jitter, Retry-After cap D2), or `DEAD`.
- **Inspect:** `resiliency/ExponentialBackoffScheduler`. It is not reused: its fixed 2 s/30 s schedule conflicts with D1. It is deleted in Step 17.
- **New files:** `WH/delivery/DeliveryRetryPolicy.java`, `WH/delivery/DeliveryProperties.java` (`webhook.delivery.*`: schedule, jitter, caps, D9 limits).
- **Depends on:** Step 4.
- **Done when:** compiled; a `Clock` and a random source are injected so the behaviour is deterministic when needed.
- **Manual check:** in Step 10, the `next_attempt_at` gaps in the DB match the schedule within ±20 %.
- **Status:** ✅ DONE (2026-11-15) at **policy level**. As implemented:
  - **`DeliveryRetryPolicy.decide(attemptNumber, AttemptOutcome) → RetryDecision`**:
    - `attemptNumber` is the 1-based attempt just made, equal to `attempt_count` after the attempt.
    - Rules in order:
      1. 2xx → `SUCCEEDED`.
      2. Not retryable per D3 → `DEAD`/`PERMANENT_FAILURE` at once. Retryability comes from `WebhookHttpSender.retryable(status, errorClass)`, the sender's own rule, so the two cannot drift; an inconsistent `retryable` flag on the outcome is ignored. `UNKNOWN` is retryable.
      3. `attemptNumber ≥ maxAttempts` (4) → `DEAD`/`ATTEMPTS_EXHAUSTED`.
      4. Otherwise `RETRY` at `now + max(jittered backoff, capped Retry-After)`.
    - **Never** produces a RETRY at or beyond attempt 4.
  - **Jitter:** delay × uniform factor in [0.8, 1.2).
  - **Retry-After:** for 429 and 503 only. Accepts delta-seconds and the three HTTP-date forms (IMF-fixdate, RFC 850, asctime; GMT), capped at 10 min. It only lengthens the delay and never changes the attempt limit. Invalid, zero, negative or past values give the normal backoff. Over-long digit strings are clamped, so no overflow is possible.
  - **`RetryDecision`:**
    - `kind`, `attemptNumber`, `nextAttemptAt`, `delay`, `retryAfterUsed`, `errorClass`, `deadReason`.
    - `deliveryStatus()` maps to `SUCCEEDED`/`RETRY`/`DEAD`.
    - The existing schema supports it with **no migration**: `RETRY` always has a non-null `next_attempt_at` (`ck_webhook_deliveries_schedule`); terminal states have it null.
  - **`DeliveryProperties` (`webhook.delivery.*`):**
    - `retry-delays` 30s,2m,10m (max attempts = size + 1 = 4), `jitter-ratio` 0.2, `retry-after-cap` 10m.
    - The D9 worker limits (4, 1, 10, 2s, 60s) are used from Step 10.
    - Values are validated at startup and set in `application.yml`.
  - **Determinism:** `Clock` and `RandomGenerator` are injectable (default system UTC; `ThreadLocalRandom` per call, which is thread-safe).
  - **Not in this step (by plan):** persisting decisions, attempt rows, lease fencing and concurrency safety are **Step 9** (`DeliveryJobRepository.recordOutcome`, fenced by `lease_owner` + `IN_FLIGHT`, inside one transaction with the attempt row; the existing unique index on (`delivery_id`, `attempt_number`) blocks duplicate attempt rows). Running attempts is **Step 10**.
  - **DLQ:** no legacy DLQ table writes and no broker DLQ publishes for outbound failures, per D1; there is no such code path.

### Step 9 — V4 migration + delivery job repository
- **Objective:** database support for safe multi-replica claiming. The Helm HPA runs 2–8 replicas.
- **Migration** `V4__delivery_worker_support.sql`:
  - Add `order_id UUID` and `occurred_at TIMESTAMPTZ` to `webhook_deliveries`, backfilled from `inbound_events`, then `NOT NULL`.
  - Add the index `(endpoint_id, order_id, created_at)`.
  - Add a partial unique index allowing one active replay per original: `(replay_of_delivery_id) WHERE replay_of_delivery_id IS NOT NULL AND status IN ('PENDING','IN_FLIGHT','RETRY')`.
  - Optional: an `error_class` or `last_error_class` column on deliveries for the DEAD list.
- **`DeliveryJobRepository`** (JDBC, like `IngestionJdbcRepository`):
  - `claimDue(owner, lease, limit)`: `UPDATE … SET status='IN_FLIGHT', lease_owner, lease_expires_at, attempt_count = attempt_count+1 WHERE delivery_id IN (SELECT … WHERE status IN ('PENDING','RETRY') AND next_attempt_at <= now() AND endpoint active AND NOT EXISTS (older non-terminal job, same endpoint + order) ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT n) RETURNING …`
  - `recordOutcome(...)`: inserts the attempt row and transitions the job, **fenced by** `WHERE lease_owner = :owner AND status = 'IN_FLIGHT'`.
  - `recoverExpiredLeases()`
- **Must also update** `IngestionJdbcRepository.insertDeliveryIfAbsent` to fill the new columns.
- **D8 (approved) also requires** `DeliveryFanOut.createDeliveries` to create jobs for **inactive** endpoints too (today it uses `findByMerchantIdAndActiveTrue`), so events arriving during a pause are queued, and the claim query to skip jobs whose endpoint is inactive.
- These two are the **only** ingestion changes allowed. Check the V3 check constraints (`ck_webhook_deliveries_*`) before writing the SQL.
- **Preserve:** V1–V3 unchanged, and all other ingestion code.
- **Depends on:** Step 4.
- **Done when:** compiled; the service starts and Flyway applies V4 to the existing local DB without errors. Check this with `docker logs`.
- **Manual check:** `\d webhook_deliveries` shows the new columns. Existing rows are backfilled: `SELECT count(*) FROM webhook_deliveries WHERE order_id IS NULL;` returns 0.
- **Status:** ✅ DONE (2026-11-15), **without the V4 migration** (re-evaluated: not required).
  - **Why no migration:**
    - The FIFO claim reads `order_id` and `occurred_at` from `inbound_events`, which is already indexed by `idx_inbound_events_order`. Copying them onto deliveries would only be an optimisation.
    - The one-active-replay unique index is only needed by replay; it **moves to Step 13** as that step's migration.
    - `last_error_class` isn't needed, because attempt rows carry `error_class`.
    - `IngestionJdbcRepository.insertDeliveryIfAbsent` is therefore unchanged.
  - **New `domain/repository/DeliveryJobRepository`** (JDBC):
    - `claimDue`:
      - CTE `SELECT … FOR UPDATE OF d SKIP LOCKED`, then `UPDATE` to `IN_FLIGHT`, `attempt_count+1`, lease = DB `now()+lease`.
      - Only due rows with `attempt_count < maxAttempts` (a fifth attempt can never be claimed), with an **active** endpoint (D8), and with no older active (PENDING/RETRY/IN_FLIGHT) job for the same endpoint and order (D7; age = event `occurred_at`, then `created_at`, then id; SUCCEEDED and DEAD don't block).
    - `completeAttempt`: the **fenced** transition, `WHERE status='IN_FLIGHT' AND lease_owner=:owner AND attempt_count=:attempt`, which clears the lease and sets `next_attempt_at = now()+delay` for RETRY, or NULL otherwise.
    - `insertAttempt`: a job-shape row (no payload, no response body; sanitised message).
    - `lockExpiredLeases`: `FOR UPDATE SKIP LOCKED`.
  - **New `delivery/DeliveryAttemptRecorder`** (`@Transactional`):
    - `claim(owner, limit)`.
    - `record(job, outcome, attemptedAt)`: decision from `DeliveryRetryPolicy`. The fenced transition runs first; a stale result writes nothing. Then the attempt row; any insert failure, including the unique `(delivery_id, attempt_number)`, rolls the transition back.
    - `recoverExpiredLeases(limit)`: writes an `UNKNOWN` attempt row and applies the normal decision, so the next try is RETRY on schedule, or DEAD if it was attempt 4.
    - Invariant: `attempt_count` equals the number of attempt rows for every non-IN_FLIGHT job.
  - **New `delivery/ClaimedDelivery`** (the fence data).
  - **D8:** `DeliveryFanOut` now uses `findByMerchantId`, so paused endpoints get `PENDING` jobs. The claim skips them until the endpoint is re-enabled. Subscription and merchant filtering are unchanged.
  - **Bug found and fixed during the check:** the `attempted_at` bind used `java.sql.Timestamp` with `TIMESTAMP_WITH_TIMEZONE`, which pgjdbc rejects; it now binds an `OffsetDateTime` in UTC.
  - **Step 10 must:** send outside any transaction; re-check `is_active` (and load URL and secret) right before sending, since an endpoint can be paused after a claim; and schedule `recoverExpiredLeases`.

### Step 10 — Delivery worker + lease recovery ⭐ milestone: merchants receive webhooks
- **Objective:** `PENDING` jobs are sent and their outcomes recorded.
- **Behaviour:**
  - A `@Scheduled` poller claims a batch (short transaction).
  - Each job runs on a bounded executor (virtual threads plus a global semaphore and per-endpoint semaphores, D9).
  - Steps per job: load the event, endpoint and mapping → payload (5) → sign (6) with the stored secret → send (7) → decide (8) → `recordOutcome` (short transaction).
  - HTTP never runs inside a DB transaction.
  - A second `@Scheduled` job recovers expired leases (`IN_FLIGHT` → `RETRY`).
  - MDC carries `deliveryId`, `eventId`, `merchantId`, `endpointId` and `attempt`.
  - A worker toggle, `webhook.delivery.worker-enabled` (default true), lets you pause it locally.
- **New files:** `WH/delivery/DeliveryWorker.java`, `WH/delivery/DeliveryProcessor.java`, `WH/delivery/LeaseRecoveryJob.java`, plus `@EnableScheduling` (on a config class).
- **Known guarantee:** at-least-once. A crash after the merchant returns 200 but before `recordOutcome` causes one re-send. Merchants deduplicate by `X-SwiftDrop-Event-Id`.
- **Depends on:** Steps 5–9. **Other members:** none, but see Section 6 for data quality.
- **Done when:**
  - Compiled; the container starts.
  - A synthetic `order.created` plus `parcel.status.changed` for the dev merchant results in signed requests at the mock receiver and `SUCCEEDED` rows.
  - A 500 endpoint retries on schedule and ends `DEAD`.
  - A 400 endpoint goes `DEAD` after 1 attempt.
- **Manual check:** Section 7.5 end to end. Restart the container while a `/slow` request is in flight: after the lease expires the job is retried, not lost.
- **Status:** ✅ DONE (2026-11-15). **Merchants now receive real event webhooks.** As implemented:
  - **`delivery/DeliveryWorker`** (`@Scheduled` every `poll-interval`):
    - Claims `min(claim-batch-size, free global permits)` jobs; when capacity is exhausted it claims nothing.
    - Dispatches each job to a virtual thread, guarded by a global `Semaphore(max-concurrency)` and a per-endpoint `Semaphore(max-per-endpoint)`. A job whose endpoint is locally busy is released at once (no attempt).
    - Permits are always released in `finally`; the poll catches everything.
    - Shutdown: no new claims; in-flight attempts get 15 s, after which leftovers are recovered via their lease.
    - Owner id: `worker-<HOSTNAME>-<random>`.
  - **`delivery/DeliveryProcessor`** (not transactional): load the event and mapping, build the payload bytes, then load the endpoint **last**, immediately before sending.
    - If it is paused, missing, or owned by another merchant, the claim is **released**: no HTTP, no attempt row, the job is back to PENDING/RETRY with its attempt count restored.
    - Otherwise: `WebhookRequest` → `WebhookHttpSender.send` (no transaction or lock held) → `DeliveryAttemptRecorder.record` (short fenced transaction).
    - An exception leaves the lease to expire, so recovery closes the attempt; nothing is lost and nothing loops forever.
  - **`delivery/LeaseRecoveryJob`:** every `lease-recovery-interval` (30 s) calls `recoverExpiredLeases(100)`.
  - **`config/DeliverySchedulingConfig`:** `@EnableScheduling`.
  - **Worker toggle:** the worker, recovery and scheduling are all conditional on `webhook.delivery.worker-enabled` (default true; env `WEBHOOK_DELIVERY_WORKER_ENABLED`).
  - **Step 9 addition:** `DeliveryJobRepository.releaseClaim` + `DeliveryAttemptRecorder.release` (fenced undo of a claim when nothing was sent). Required by the pause-between-claim-and-send case and the per-endpoint limit.
  - **Config (`application.yml`):** `worker-enabled`, `lease-recovery-interval: 30s`; D9 values unchanged (4 / 1 / 10 / 2s / 60s). **The limits are per replica:** with N replicas, up to 4N attempts in the cluster and N per endpoint. FIFO (D7) is cluster-wide (enforced by the claim query).
  - **MDC:** `deliveryId`, `eventId`, `merchantId`, `endpointId`, `attempt` (the default log pattern doesn't print MDC; the messages carry the ids).
  - **Known:**
    - At-least-once delivery: a crash after send causes one re-send, observed in the check below.
    - An attempt refused by the URL policy is recorded as `BLOCKED_DESTINATION` without HTTP (Step 7 design).
    - A processing exception before send is recorded as `UNKNOWN` by lease recovery (the defined exception).

### Step 11 — Move test ping onto the new sender
- **Objective:** ping uses payload, signer and sender once, synchronously, with no retry and no DB job. It records a legacy-shape attempt or none.
- **Why:** pings are then signed exactly like real deliveries, and the legacy dispatcher loses its last ping usage.
- **Modify:** `WebhookSubscriptionService.testPing`.
- **Depends on:** Steps 1, 3, 5, 6 and 7.
- **Done when:** compiled; the ping signature verifies with the Section 7.6 script.
- **Status:** DONE by the team in PR #179 (2026-11-22), confirmed in code on 2026-11-27. `TestPingService` sends one
  `webhook.test.ping` v1 envelope through `WebhookHttpSender` (same signer, headers, URL policy and connect-time guard),
  records one legacy-shape attempt row (no delivery job, no retry, no DLQ). Tested by `TestPingServiceTest` (mocked
  sender) and `WebhookDeliveryFlowIT.endpointManagement` (real request).

### Step 12 — Delivery history read API
- **Objective:** let merchants and admins see deliveries and attempts.
- **Endpoints:**
  - `GET /api/v1/webhooks/deliveries?status=&endpointId=&orderId=&page=`
  - `GET /api/v1/webhooks/deliveries/{deliveryId}` (with attempts)
- **Scoping:** merchant-scoped through `MerchantAccess`; another merchant's delivery returns 404.
- **Response content:** no secrets, no payload bodies from the merchant, and no target URL in list views.
- **New files:** `controller/DeliveryController.java`, `service/DeliveryQueryService.java`, DTO records.
- **Also update:** the OpenAPI spec and the generated frontend types.
- **Depends on:** Step 9 (Step 10 for meaningful data).
- **Done when:** compiled; in dev mode the list shows only the dev merchant's rows.
- **Status:** PARTIAL, now optional (2026-11-27). PR #179 covers the DEAD list (`/deliveries/failed`) and the
  per-endpoint attempts list; there is no general delivery list or per-delivery detail. See
  [remaining work](#remaining-work-re-ordered-2026-11-27).

### Step 13 — DEAD list + asynchronous replay; legacy replay becomes read-only
- **Objective:** `POST /api/v1/webhooks/deliveries/{id}/replay` returns 202 and inserts a replay row (D10). It is idempotent while one is active. `DEAD` deliveries can be listed through Step 12's filter `status=DEAD`.
- **Legacy changes:**
  - `/api/v1/webhooks/dlq/{id}/replay` and `/replay-all` stop sending HTTP. Return 410 or redirect the logic.
  - The legacy list stays read-only as history.
- **Modify:** `DlqReplayController`, `DlqReplayService`.
- **New files:** `service/DeliveryReplayService.java`.
- **Depends on:** Steps 9, 10 and 12.
- **Done when:**
  - Compiled.
  - Replaying a `DEAD` job creates exactly one new row, even with a double-click.
  - The worker sends it with the same `X-SwiftDrop-Event-Id`.
- **Migration moved here from Step 9:** `V4__…`: a partial unique index allowing one active replay per original, `(replay_of_delivery_id) WHERE replay_of_delivery_id IS NOT NULL AND status IN ('PENDING','IN_FLIGHT','RETRY')` (D10).
- **Status:** DONE (2026-11-22); the legacy `/dlq` replay endpoints stopped sending in Step 17 (2026-11-27, 410 Gone). List and replay are `GET /api/v1/webhooks/deliveries/failed`, `POST .../failed/{id}/replay` and `POST .../failed/replay` (`FailedDeliveryService`); the index is `V4__one_active_replay.sql`; tests are `FailedDeliveryReplayIT`.

### Step 14 — Frontend console update
- **Objective:**
  - Delivery history table with an attempts drawer.
  - DEAD list with a replay button.
  - Remove the legacy replay buttons.
  - Update the copy ("3-attempt exponential backoff" becomes the real D1 schedule).
- **Modify:** `frontend/src/features/merchant/pages/WebhookResiliencyConsolePage.tsx`, `components/WebhookDlqReplayTable.tsx`, `services/api/webhookApi.ts` (generated types only).
- **Optional new file:** `WebhookSettingsCard.tsx` (named in the member guide).
- **Rules:** `frontend/AGENTS.md`: SwiftDrop design-system components, compact density, and generated types only.
- **Split if large:** 14a history; 14b DEAD/replay.
- **Depends on:** Steps 12 and 13.
- **Done when:** `npm run lint && npm run build` pass, and the flows work in dev mode.
- **Status:** DONE (2026-11-22) for failed deliveries, replay, copy and event checkboxes. Full delivery history is still the attempts dialog. Test pings use the real sender (`TestPingService`), and merchants have an API guide page.

### Step 15 — Secret encryption at rest + rotation (required before production)
- **Objective:**
  - `V5` adds a `secret_ciphertext` column (plus a key version).
  - Existing secrets are encrypted at startup or by migration logic, then the plaintext column is nulled. The column is dropped in a later migration.
  - Add `SecretCipher` (AES-256-GCM, random 96-bit nonce).
  - Add `POST /api/v1/webhooks/subscriptions/{id}/rotate-secret`, which returns the new secret once.
- **Env:** `WEBHOOK_SECRET_KEY` (base64 32 bytes). Mirror it in `.env.example`, `docker-compose.yml`, Helm `values.yaml`/`secretEnv`, `values-secrets.example.yaml` and `scripts/generate-helm-secrets.sh` (per `backend/AGENTS.md`). The service **fails to start** without it outside the local profile.
- **New files:** `WH/security/SecretCipher.java`, `V5__encrypt_endpoint_secrets.sql`, a rotate DTO.
- **Depends on:** Steps 2 and 10 (the worker reads the secret through one accessor so only that accessor changes).
- **Other members:** the infra owner (compose/Helm/secret scripts).
- **Done when:** compiled; the DB holds no plaintext secret, signing still verifies, and rotation takes effect on the next attempt.
- **Status:** NOT STARTED

### Step 16 — Observability
- **Objective:**
  - Micrometer meters: `webhook.delivery.attempts{outcome,errorClass}`, a latency timer, and gauges for due backlog, oldest due age, `AWAITING_MERCHANT` count and `DEAD` count.
  - Expose `metrics` in actuator.
  - Logs contain no URLs or secrets.
- **Modify:** `application.yml`, `application-aks.yml`, worker classes. No new dependency (Micrometer comes with actuator).
- **Depends on:** Step 10.
- **Done when:** `GET http://localhost:8087/actuator/metrics/webhook.delivery.attempts` returns data.
- **Status:** NOT STARTED

### Step 17 — Remove legacy dispatch code
- **Objective:**
  - Delete `OutboundWebhookDispatcher`, `ExponentialBackoffScheduler`, and `HmacSignatureGenerator` if it is unused.
  - Stop writing `dead_letter_webhook_records`. Keep the table and the read-only list as history.
- **Depends on:** Steps 11 and 13.
- **Done when:** no references remain and the module compiles.
- **Status:** DONE (2026-11-27). The legacy dispatcher, its signature and backoff classes and `WebClientConfig` are
  deleted; nothing writes `dead_letter_webhook_records`. `GET /api/v1/webhooks/dlq` and `GET /dlq/{id}` remain as
  read-only history for MERCHANT (own records) and SYSTEM_ADMIN (DISPATCHER now 403). `POST /dlq/{id}/replay` and
  `POST /dlq/replay-all` answer **410 Gone** (`LegacyReplayRetiredException`) without reading, sending or writing
  anything. The table, its rows, the migrations and the RabbitMQ inbound DLQ are unchanged. Verified by `verify`
  (see the progress log).

### Step 18 — Merchant order reference (coordinated; blocked on Member 3)
- **Objective:** merchants can correlate a webhook with their own order.
- **Changes:**
  - After Member 3 publishes an optional `externalRef` on `OrderCreatedEvent` (with a back-compatible constructor), `V6` adds `merchant_order_ref` to `order_merchant_map`.
  - The decoder and `insertOrderOwnershipIfAbsent` store it. This is a minimal ingestion change.
  - The payload's `merchantOrderReference` gets filled.
- **Depends on:** Step 5 plus the Member 3 / contracts change. **Do not edit `CC/` yourself.**
- **Status:** BLOCKED (waiting for Member 3). Re-checked 2026-11-27: `OrderCreatedEvent` still has no `externalRef`.
  The order service **already stores** it (`CustomerOrderService.createMerchantOrder` → `CustomerOrder.externalRef`),
  so only the event field and its publisher are missing.

### Remaining work, re-ordered 2026-11-27

Old step numbers are kept as names. Order chosen by risk and dependencies, not by number.

| # | Task | Status | Why still needed | Depends on / coordination | Tests required | Manual verification |
|---|---|---|---|---|---|---|
| R0 | Report N1 (order-service cancel event) and N2 (double `PICKUP_ASSIGNED`) | To do (message, no code) | N1 means merchants never hear about most cancellations | Members 3, 5, 6 | None here; after Member 3's fix, add an ingestion IT that a real-shaped cancel (`PICKUP_ASSIGNED → CANCELLED`) creates deliveries | `LOCAL-TESTING.md` B.7 cancel via the order API |
| R1 | Merchant API guide: ordering and status semantics (N3, N4) | **DONE 2026-11-27** (see the progress log) | — | — | `npm run lint`, `npm test`, `npm run build` | `/merchant/api-guide` → Receive updates |
| R2 | **Step 17: retire the legacy path** | **DONE 2026-11-27** (see Step 17) | N6: last live sender with the old signature, HTTP inside a transaction, DISPATCHER read access; doing it before Step 15 means one fewer reader of the plaintext secret to migrate | None (no migration; API change to `/dlq` replay → 410 or removal; spec + generated types) | Delete or replace `HmacSignatureGeneratorTest`, `ExponentialBackoffSchedulerTest`; update `DlqReplayServiceTest`, `DlqReplayControllerTest`, `WebhookDeliveryFlowIT.legacyDeadLetterReplay` (assert 410, no request); keep the 85 %/68 % gate; `verify` | `POST /dlq/{id}/replay` refused, no request reaches the mock; `GET /dlq` still lists history for MERCHANT/SYSTEM_ADMIN |
| R3 | Test hardening for Step 9–10 code | **DONE 2026-11-27** (`DeliveryLeaseFencingIT`, `DeliveryWorkerLimitsTest`, `ConnectTimeAddressGuardTest`; see the progress log). Remaining: exact lease-expiry boundary, timeouts, Retry-After end to end | No test covers lease recovery, stale-result fencing, `releaseClaim`, concurrent claimers, pause between claim and send, the connect-time resolver guard, timeouts or Retry-After end to end | None | New ITs on the `WebhookDeliveryFlowIT`/`FailedDeliveryReplayIT` setups (Testcontainers PostgreSQL; a loopback receiver); a unit test for `BlockingAddressResolverGroup` | None beyond `verify` |
| R4 | **Step 15: secret encryption + rotation** | Not started | H1: secrets are plaintext in `webhook_endpoints.secret_key`; required before production | **Member 9**: `WEBHOOK_SECRET_KEY` is in none of `docker-compose.yml`, `.env.example`, Helm `values.yaml`/`values-secrets.example.yaml` or `scripts/generate-helm-secrets.sh` (checked 2026-11-27). New migration V5: back up the hosted DB first (runbook). | `SecretCipher` unit tests; migration IT (V5 applied, no plaintext left); signing still verifies end to end; rotate endpoint (owner only, other merchant 404, new secret works on the next attempt) | Rotate in the console; the next delivery verifies only with the new secret; `SELECT secret_key` shows no plaintext |
| R5 | Step 16: metrics | Not started | No visibility of backlog, DEAD count or latency | Expose `metrics` in actuator (check gateway/ingress keep `/actuator` private) | Meter registration test or an IT asserting meters after a delivery | `GET http://localhost:8087/actuator/metrics/webhook.delivery.attempts` |
| R6 | Step 12 remainder: delivery history | Optional | Merchants see failures and attempts already | None | Controller + scoping IT (another merchant → 404) | Console |
| R7 | Step 18 | Blocked | — | Member 3 (and Member 7 for the name) | Decoder + ingestion IT for the stored reference | B.5 order with `externalRef` |

Cross-member items re-checked on 2026-11-27: C3 **fixed** (order service); the delivery-execution tracking number is
now only a fallback for old events (**mitigated**); publish-after-commit in the order service still **open**
(Member 3); random correlation ids in delivery events still **open** (Member 6, `PublishStateTransitionAction`);
`MerchantEventPublisher.publishOrderCreated` still has **no callers** (Member 7); `WEBHOOK_SECRET_KEY` still **absent**
(Member 9).

### Deferred / optional (only if time allows)
- **Endpoint health:** auto-disable an endpoint after N consecutive DEAD jobs, with the reason shown in the UI.
- **Retention:** clean up old `SUCCEEDED` jobs and `NO_MERCHANT` inbox rows, and alert on stale `AWAITING_MERCHANT`.
- **Rotation grace period:** sign with both secrets during a grace window.
- **Egress NetworkPolicy:** in Helm, block cluster-internal ranges from webhook pods (infra owner).
- **Remove the `webhook.dispatch.requested` binding:** requires Member 9 and parity with `definitions.json`. It is harmless as it is.

### Changes vs. the audit roadmap
- **Moved earlier:**
  - Ping hardening and the SSRF registration policy are now Steps 1 and 3.
  - Hiding secrets from GET/list is split from encryption and done first (Step 2), because it is a cheap fix for a live leak.
- **Split:**
  - Secrets: Step 2 now (hide), Step 15 later (encrypt and rotate).
  - SSRF: Step 3 (registration) and Step 7 (connect-time).
  - API: Step 12 (read API) and Step 13 (replay).
  - Frontend: 14a and 14b.
- **Removed:**
  - The failure-matrix and IT steps (the no-tests decision).
  - The separate ADR file: decisions now live in Section 4.
- **Deferred:** endpoint auto-disable, retention, rotation grace, egress policy.
- **Unchanged:** the ingestion design, V3 schema usage, the FIFO-per-order claim, and at-least-once semantics.

---

## 6. Cross-member dependencies

The webhook service must **not** try to solve upstream ownership problems by trusting unverified merchant data. Report these issues, and do not patch other services.

| Problem | Responsible | Why it affects webhooks | What is needed | Blocks |
|---|---|---|---|---|
| A customer can set any `merchantId` on `POST /api/v1/orders`: `CreateOrderRequest.merchantId` flows into `OrderCreatedEvent.merchantId` | **Member 3** | First-claim-wins ownership sends that order's events (COD, values, statuses) to the victim merchant. This cannot be detected downstream. | Ignore or reject `merchantId` on the public customer endpoint. Set it only on the internal merchant path (`/internal/v1/orders`). | **FIXED (PR #203):** the order service sets `merchantId` only for `OrderSource.MERCHANT`, and the webhook decoder ignores a `merchantId` that differs from `customerId`. |
| Order-service cancellation event carries `previousPublicStatus = CANCELLED` (N1) | **Member 3** | Dropped as `NO_CHANGE`; merchants get no cancellation webhook | Read the status before changing it in `cancelOrder`/`rejectFeeRevision`, or pass it to `publishOrderCancelled` | Merchant-visible correctness (R0) |
| `order.created` and `parcel.status.changed` are published inside `@Transactional` (`CustomerOrderService.persistNewOrder`, cancel paths) | **Member 3** | A rollback (for example losing the `(merchantId, externalRef)` race) still emits events, so the merchant gets webhooks for a phantom order | Publish after commit (`TransactionSynchronization` / `@TransactionalEventListener`) | Data quality from Step 10 on |
| No merchant order reference in events | **Member 3** + contracts owner (+ Member 7 to confirm the field name) | The merchant cannot match a webhook to its own order | Add an optional `externalRef` to `CC/events/order/OrderCreatedEvent` with a back-compatible constructor. Call sites: customer-order `OrderEventPublisher`, dispatch `OrderCreatedConsumerIT`, `CourierMatchingEngineTest`. | Step 18 |
| delivery-execution makes its own tracking number, `"SD-" + orderId[0..8]` (`DeliveryExecutionService.handleCourierAssigned`) | **Member 6** | The merchant would see two tracking numbers. Mitigation in this service: the payload uses `order_merchant_map.tracking_number` (Step 5). | Use `CourierAssignedEvent.trackingNumber` | None. **2026-11-27:** delivery now uses the event's tracking number; `"SD-"+orderId` is only a fallback for old events. |
| A random correlationId on every delivery-execution event (`PublishStateTransitionAction.doPublish`) | **Member 6** | Traces break between the request and the webhook | Propagate the incoming correlationId | None (only affects logs) |
| `PICKUP_ASSIGNED` is published by both dispatch (`ManualOverrideService.publishAssignmentEvents`) and delivery-execution (`handleCourierAssigned`): confirmed in source 2026-11-27 (N2), not yet observed at runtime | **Members 5 + 6** | Merchants get two PICKUP_ASSIGNED webhooks with different event ids | Agree on one owner | None. Dedupe is not possible here because the event ids differ. |
| `MerchantEventPublisher.publishOrderCreated` (merchant-b2b) has no callers | **Member 7** | If it is wired up, a second ownership claim appears: a disagreement goes to the DLQ, an agreement is harmless | Remove it or document that it must not be used | None |
| `merchantId` JWT claim for approved merchants | **Member 2 / Member 7** | `MerchantAccess` needs `X-Merchant-Id` for MERCHANT callers | Confirm the claim is issued after approval | **Resolved:** IAM adds it after `merchant.approved` (the seeder waits for the claim), and the gateway forwards it as `X-Merchant-Id` |
| `WEBHOOK_SECRET_KEY` in compose, `.env.example`, Helm and secret scripts | **Infra owner (Member 9)** | Required for Step 15 | Agree on a name and add mirrored config | Step 15 |
| The dev SSRF allowlist reaching the container | Nobody (resolved) | Local ping and delivery to the host mock | **Decided in Step 3:** a personal, gitignored `docker-compose.override.yml`. No shared infrastructure change, so no coordination is needed. | — |
| `definitions.json` | **Member 9** | Topology parity | No change needed. The member guide's "broker config drift" item (`docs/member-guides/member-8.md`) is **outdated**: the files match. | — |

---

## 7. Local development and manual verification

> **The current, verified procedures are in [`docs/LOCAL-TESTING.md`](docs/LOCAL-TESTING.md)** (2026-11-27): synthetic
> events (Method A) and the real parcel lifecycle through the gateway (Method B), for every public status. The
> subsections below are the original Step 0–10 notes. Where they disagree with the guide, the guide wins. In
> particular: Test Ping now uses the real sender (7.7's first sentence is out of date), deliveries are sent by the
> worker (7.5's "stays PENDING" note is out of date), and a synthetic `order.created` that includes `customerId` must
> use the merchant id as `customerId`, or the order belongs to nobody (PR #203).

### 7.1 Minimum setup and which "login" is which

There are **two unrelated logins**:

| Prompt | Belongs to | Credentials | Needed for webhook work? |
|---|---|---|---|
| SwiftDrop frontend `/login` and `/register` | The SwiftDrop app (auth-iam-service through api-gateway). The **browser** route guard `frontend/src/app/RoleGuard.tsx` redirects to `/login` when no token is in `sessionStorage`. | Real user accounts | **No.** Use dev mode (7.2). |
| `http://localhost:15672` | RabbitMQ's own management UI | User **`swiftdrop`**, password **`changeme_rabbit`** | Yes, for inspecting and publishing events |

**Where the RabbitMQ credentials come from:** they are defined twice and agree.
- `docker-compose.yml` sets `RABBITMQ_DEFAULT_USER`/`RABBITMQ_DEFAULT_PASS` from `.env` (`swiftdrop` / `changeme_rabbit`).
- `infra/local/rabbitmq/definitions.json`, loaded at broker start, defines the user `swiftdrop` with a password hash. That hash was verified to equal `changeme_rabbit`.

If the login fails, `.env` was probably changed after the volume was created. The definitions file still sets `changeme_rabbit`.

**Containers needed** (no auth-iam-service, no api-gateway, no redis):

| Container (compose service) | Port |
|---|---|
| `swiftdrop-rabbitmq` (`rabbitmq`) | 5672, 15672 |
| `swiftdrop-webhook-postgres` (`webhook-postgres`) | 5438 |
| `swiftdrop-webhook-resiliency-service` (`webhook-resiliency-service`, profile `apps`) | 127.0.0.1:8087 |

```powershell
# from the repo root
docker compose up -d rabbitmq webhook-postgres
docker compose --profile apps up -d --build webhook-resiliency-service   # rebuild after code changes
docker compose ps
docker logs -f swiftdrop-webhook-resiliency-service
# stop (keeps data; never use "down -v")
docker stop swiftdrop-webhook-resiliency-service swiftdrop-rabbitmq swiftdrop-webhook-postgres
```

Naming `webhook-resiliency-service` with `--profile apps` starts only that service and its `depends_on` (rabbitmq and webhook-postgres), not the other apps.

**Compiling on this PC.** The service targets Java 25. The host has JDK 21 and 24 only, so `mvn … compile` on the host fails with `release version 25 not supported`. Compile inside the Maven image that the Dockerfile already uses:

```powershell
# from the repo root (PowerShell); uses your local ~/.m2 cache
docker run --rm -v "${PWD}\backend:/workspace" -v "$env:USERPROFILE\.m2:/root/.m2" -w /workspace `
  maven:3-eclipse-temurin-25 mvn -B -q --no-transfer-progress -pl webhook-resiliency-service -am -DskipTests compile
```

Alternatively, install JDK 25 and use `mvn -f backend/pom.xml -pl webhook-resiliency-service -am -DskipTests compile`.

### 7.2 Local dev mode (no auth service, no gateway)

**How it works.** The frontend is set up so that nothing in the backend changes:

1. **Browser.**
   - `npm run dev:webhook` runs Vite in mode `webhook-dev`, which loads `frontend/.env.webhook-dev`.
   - With `VITE_DEV_AUTH_BYPASS=true`, `frontend/src/app/devAuthBypass.ts` (called from `main.tsx`) starts a fake **MERCHANT** dev session when nobody is signed in, so `RoleGuard` stops redirecting to `/login`.
   - This uses the existing dev-session mechanism (`useAuthStore.setSession`, the same one used by the `PersonaSwitcherDevBar`).
2. **Dev server proxy** (`frontend/vite.config.ts`).
   - With `WEBHOOK_DEV_DIRECT=true`, requests to `/api/v1/webhooks/**` go **directly** to `http://localhost:8087` instead of the gateway on 8080.
   - The proxy plays the gateway's trust-boundary role: it removes `Authorization` and any client-sent `X-User-*`/`X-Merchant-Id`/`X-District-Id` headers, then sets a fixed identity:
     - `X-User-Id: 00000000-0000-4000-8000-0000000000d1`
     - `X-User-Role: MERCHANT`
     - `X-Merchant-Id: 00000000-0000-4000-8000-0000000000a1` (**the dev merchant**: use this merchantId in synthetic `order.created` events)
   - All other `/api` calls still go to 8080 and fail when the gateway is not running. That is expected: only the webhook console is meant to work.

**Why this is safe:**
- **Backend untouched.** No backend code, profile or security setting changed. The webhook service still requires gateway-style headers.
- **Opt-in only.** The bypass only exists in the Vite **dev server** started with `--mode webhook-dev`. `npm run dev`, `npm run build`, `vite preview` and the Docker/nginx frontend never enable it, and `import.meta.env.DEV` is false in builds.
- **Loopback only.** The proxy refuses any non-loopback `WEBHOOK_DEV_TARGET`. Vite listens on localhost only by default, and the webhook port is published on `127.0.0.1` only.
- **Rule compliant.** Browser code still calls `/api/v1/...` (the frontend rule "do not call downstream service ports from browser code" holds); only the local dev server reroutes.

**Use it:**

```powershell
cd frontend
npm run dev:webhook
# open http://localhost:5173/merchant/webhooks
```

The landing page `/merchant` (the dashboard) calls other services and shows errors; go straight to `/merchant/webhooks`. If a previous real or dev session exists in this tab, it is kept. Use **Sign out**, or open a new tab, to get a clean bypass session.

**Change the identity locally without editing the committed file:** create `frontend/.env.webhook-dev.local` (gitignored by the `.env.*.local` rule), for example `WEBHOOK_DEV_MERCHANT_ID=<uuid>`. `WEBHOOK_DEV_ROLE=SYSTEM_ADMIN` lists every merchant's data, but creating an endpoint as admin requires a `merchantId` in the body, which the UI does not send, so keep MERCHANT for normal work.

**Turn it off:** stop the dev server and run `npm run dev` (normal mode, which proxies everything to the gateway on 8080). Sign out, or close the tab, to drop the dev session.

**Restore the full authenticated app:**

```powershell
docker compose --profile apps up -d --build   # the whole app profile
cd frontend; npm run dev
```

`api-gateway` declares `depends_on` (healthy) on **every** app service: auth-iam, customer-order, pricing, dispatch, delivery, merchant-b2b, webhook, notification and analytics, plus redis and rabbitmq. So `docker compose --profile apps up -d api-gateway` also starts the whole stack. This needs a lot of RAM, which is the reason dev mode exists.

### 7.3 Calling the API directly (no frontend)

```powershell
$h = @{ 'X-User-Id'='00000000-0000-4000-8000-0000000000d1'; 'X-User-Role'='MERCHANT';
        'X-Merchant-Id'='00000000-0000-4000-8000-0000000000a1' }
Invoke-RestMethod http://localhost:8087/api/v1/webhooks/subscriptions -Headers $h
Invoke-RestMethod -Method Post http://localhost:8087/api/v1/webhooks/subscriptions -Headers $h -ContentType 'application/json' `
  -Body '{"targetUrl":"http://host.docker.internal:9090/ok","subscribedEvents":"parcel.status.changed,order.created","description":"dev"}'
```

- **Health:** `http://localhost:8087/actuator/health`.
- **OpenAPI:** `http://localhost:8087/v3/api-docs` (springdoc default). Swagger UI is at `http://localhost:8087/swagger-ui.html`. The gateway exposes the same docs as `/api/v1/webhooks/v3/api-docs`. Swagger "try it out" does not send identity headers, so use PowerShell or curl.
- **Target URL:** the service runs in Docker, so a mock on the host is reached through `host.docker.internal`, not `localhost`. Since Step 3, plain-HTTP and private destinations are refused unless the exact `host:port` is on the dev allowlist (§7.2a). Public HTTPS receivers such as webhook.site need no exception.

### 7.2a Dev exception for the local mock receiver (SSRF allowlist)

> **Default local state: DISABLED** (since the end-of-session review on 2026-11-15). The gitignored `docker-compose.override.yml` stays in place with `WEBHOOK_SECURITY_DEV_HTTP_ALLOWLIST: ""`; its comments show the one line to change to enable it. Enable it only for a mock-receiver session, then disable it again. webhook.site (public HTTPS) never needs it.

Create `docker-compose.override.yml` in the repo root. It is already listed in `.gitignore`, so it stays local, and Docker Compose merges it automatically:

```yaml
services:
  webhook-resiliency-service:
    environment:
      WEBHOOK_SECURITY_DEV_HTTP_ALLOWLIST: host.docker.internal:9090
```

Then recreate the container: `docker compose --profile apps up -d --no-deps webhook-resiliency-service`. Add `--build` after code changes.

- **Confirm it is on:** `docker logs swiftdrop-webhook-resiliency-service 2>&1 | Select-String "DEVELOPMENT ONLY"`.
- **What it allows:** only `host.docker.internal:9090`, over HTTP or HTTPS. Every other URL, including `host.docker.internal` on any other port, `localhost` and private IPs, is still refused.
- **Turn it off:** delete the file and recreate the container. The startup warning then disappears.
- **Production safety:**
  - The property defaults to empty.
  - Helm sets `SPRING_PROFILES_ACTIVE=aks`, and `WebhookUrlPolicy` refuses to start when the allowlist is non-empty under `aks`.
  - Neither compose nor Helm sets the variable.

### 7.4 Publishing synthetic events (RabbitMQ)

The UI route is http://localhost:15672 → Exchanges → `swiftdrop.events.exchange` → *Publish message*. Use the routing key and payload below, plus the optional header `x-event-schema-version` = `1`.

The PowerShell equivalent:

```powershell
$cred = [pscredential]::new('swiftdrop', (ConvertTo-SecureString 'changeme_rabbit' -AsPlainText -Force))
function Publish($key, $json) {
  $b = @{ properties=@{}; routing_key=$key; payload=$json; payload_encoding='string' } | ConvertTo-Json -Depth 5
  Invoke-RestMethod -Method Post 'http://localhost:15672/api/exchanges/%2F/swiftdrop.events.exchange/publish' -Credential $cred -ContentType 'application/json' -Body $b
}
$order = [guid]::NewGuid()
Publish 'order.created' (@{ eventId=[guid]::NewGuid(); correlationId=[guid]::NewGuid(); orderId=$order; trackingNumber='SD-DEV-0001';
  merchantId='00000000-0000-4000-8000-0000000000a1'; timestamp=(Get-Date).ToUniversalTime().ToString('o') } | ConvertTo-Json)
Publish 'parcel.status.changed' (@{ eventId=[guid]::NewGuid(); correlationId=[guid]::NewGuid(); orderId=$order;
  previousPublicStatus='CREATED'; newPublicStatus='IN_TRANSIT'; timestamp=(Get-Date).ToUniversalTime().ToString('o') } | ConvertTo-Json)
```

- **Response:** `{"routed": true}` means a queue received it.
- **Early-arrival check:** publish the status event *before* `order.created`. The row stays `AWAITING_MERCHANT` and resolves when `order.created` arrives.
- **Malformed check:** a bad payload ends up in `dlq.webhook.events.queue`. Inspect it via Queues → *Get messages*, with Ack mode "Nack message requeue true" to peek without removing.
- **Required fields:** these follow the decoder (`InboundEventDecoder`). If a publish is rejected, check the service log for the validation reason.

### 7.5 Inspecting the database

```powershell
docker exec -it swiftdrop-webhook-postgres psql -U swiftdrop -d swiftdrop_webhook
```

```sql
SELECT event_id, event_type, processing_status, merchant_id, order_id FROM inbound_events ORDER BY received_at DESC LIMIT 20;
SELECT order_id, merchant_id, tracking_number FROM order_merchant_map ORDER BY order_id LIMIT 20;
SELECT delivery_id, endpoint_id, status, attempt_count, next_attempt_at, lease_owner FROM webhook_deliveries ORDER BY created_at DESC LIMIT 20;
SELECT delivery_id, attempt_number, http_status_code, error_class, latency_ms FROM webhook_delivery_attempts ORDER BY attempted_at DESC LIMIT 20;
SELECT endpoint_id, merchant_id, target_url, is_active FROM webhook_endpoints;
```

**Until Step 10 exists, every `webhook_deliveries` row stays `PENDING`. That is expected, not a bug.** Only the test ping makes HTTP calls today.

### 7.6 Mock merchant receiver and signature check

**Option A — WireMock** (no repo change, about 150 MB):

```powershell
docker run -d --name wh-mock -p 9090:8080 wiremock/wiremock:3.9.1
```

Create stubs through `POST http://localhost:9090/__admin/mappings`:

| Path | Behaviour |
|---|---|
| `/ok` | 200 |
| `/bad` | 400 |
| `/rate` | 429 + `Retry-After: 30` |
| `/boom` | 500 |
| `/slow` | `fixedDelayMilliseconds: 10000` |
| `/redirect` | 302 |
| `/flaky` | scenario: 500, 500, then 200 |

Inspect received requests and headers at `GET http://localhost:9090/__admin/requests`. Remove the mock with `docker rm -f wh-mock`.

**Option B — Python** (lighter). Save this outside the repo (for example in `%TEMP%`) and run `python mock_merchant.py`:

```python
import http.server, time
class H(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        print(self.path, dict(self.headers), body.decode(errors='replace'), flush=True)
        code = {'/ok': 200, '/bad': 400, '/rate': 429, '/boom': 500, '/redirect': 302}.get(self.path, 200)
        if self.path == '/slow': time.sleep(10)
        self.send_response(code)
        if code == 429: self.send_header('Retry-After', '30')
        if code == 302: self.send_header('Location', 'http://example.com')
        self.end_headers()
http.server.ThreadingHTTPServer(('0.0.0.0', 9090), H).serve_forever()
```

**Signature check (contract from Step 6; real deliveries from Step 10, test pings from Step 11).** Recompute the HMAC with the endpoint secret, exactly as shown at creation including `whsec_`, and compare it with the received `X-SwiftDrop-Signature`:

```python
import hmac, hashlib, time
# SECRET: endpoint secret; TIMESTAMP: X-SwiftDrop-Timestamp header (string); BODY_BYTES: raw request body bytes
expected = 'sha256=' + hmac.new(SECRET.encode('utf-8'), TIMESTAMP.encode('ascii') + b'.' + BODY_BYTES, hashlib.sha256).hexdigest()
fresh = abs(time.time() - int(TIMESTAMP)) <= 300
valid = fresh and hmac.compare_digest(expected, SIGNATURE_HEADER)
```

---

### 7.7 Real event delivery (from Step 10)

Test Ping still uses the legacy dispatcher until Step 11. **Real events** are delivered by the worker. To trigger one, publish a synthetic event pair for the dev merchant through RabbitMQ (tested in Windows PowerShell 5.1 on 2026-11-15):

```powershell
$merchantId = '00000000-0000-4000-8000-0000000000a1'
$auth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('swiftdrop:changeme_rabbit')) }
$publishUrl = 'http://localhost:15672/api/exchanges/%2F/swiftdrop.events.exchange/publish'
$orderId = [guid]::NewGuid().ToString()
$now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
$orderCreated = @{ eventId = [guid]::NewGuid().ToString(); correlationId = [guid]::NewGuid().ToString(); orderId = $orderId; trackingNumber = 'SD-GUIDE-0001'; merchantId = $merchantId; speed = 'STANDARD'; timestamp = $now } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri $publishUrl -Headers $auth -ContentType 'application/json' -Body (@{ properties = @{}; routing_key = 'order.created'; payload = $orderCreated; payload_encoding = 'string' } | ConvertTo-Json -Compress)
$statusChanged = @{ eventId = [guid]::NewGuid().ToString(); correlationId = [guid]::NewGuid().ToString(); orderId = $orderId; previousPublicStatus = 'CREATED'; newPublicStatus = 'IN_TRANSIT'; timestamp = $now } | ConvertTo-Json -Compress
Invoke-RestMethod -Method Post -Uri $publishUrl -Headers $auth -ContentType 'application/json' -Body (@{ properties = @{}; routing_key = 'parcel.status.changed'; payload = $statusChanged; payload_encoding = 'string' } | ConvertTo-Json -Compress)
"orderId = $orderId"
```

Every **active** endpoint of that merchant subscribed to the event type receives it. Watch with:

```sql
SELECT e.description, d.status, d.attempt_count, d.next_attempt_at, d.delivery_id
FROM webhook_deliveries d JOIN webhook_endpoints e ON e.endpoint_id = d.endpoint_id
JOIN inbound_events i ON i.event_id = d.inbound_event_id
WHERE i.order_id = '<orderId>' ORDER BY d.created_at;
```

## Progress log

Append one entry per completed task. Never mark a step done unless its "Done when" criteria were met.

| Date | Step | Result | Notes |
|---|---|---|---|
| 2026-11-14 | 0 | Done | Plan created. Deleted 11 test files in `src/test`. Frontend dev mode added (`frontend/.env.webhook-dev`, `vite.config.ts`, `src/app/devAuthBypass.ts`, `src/main.tsx`, `package.json` script `dev:webhook`). Fixed the README broker diagram. Backend compiled in the JDK 25 Maven container; frontend lint and build passed. No backend production code changed. |
| 2026-11-15 | 1 | Done | **Changed:** `OutboundWebhookDispatcher`, `WebhookSubscriptionService`, `DlqReplayService`, `DlqFailureClassifier`, `WebhookSubscriptionController` (operation docs), `WebhookDeliveryAttemptResponse`; new `service/AttemptResponseMapper`; spec `docs/api-specs/webhook-resiliency-service.yaml`; regenerated `frontend/src/types/generated/webhook-resiliency-service.ts` only; `WebhookResiliencyConsolePage.tsx`. **Build:** compiled (container), frontend lint and build passed, no automated tests. **Manually verified** against the rebuilt container and a host Python mock via `host.docker.internal:9090`: `/ok` returns 200, success, latency recorded; `/boom` returns 500, `HTTP_SERVER_ERROR`; a closed port gives a null status and `CONNECTION_FAILURE`. Exactly one POST per ping reached the mock. DLQ row count stayed 0. The API and new rows contain no response body. A scan of service logs for the host, ports and body text found 0 hits. **Not performed:** browser UI check, webhook.site check. **Left behind:** three dev endpoints named "step1-verify …" for merchant `…a1`. |
| 2026-11-15 | 2 | Done | **Changed:** `dto/WebhookEndpointResponse` (secret removed), new `dto/WebhookEndpointCreatedResponse`, `WebhookSubscriptionService.createEndpoint`/`mapToResponse`, `WebhookSubscriptionController.createEndpoint`; spec; regenerated webhook types only; `frontend/src/services/api/webhookApi.ts`; `WebhookResiliencyConsolePage.tsx` (one-time dialog, row reveal removed). **Build:** compiled (container), frontend lint and build passed, no automated tests. **Manually verified** against the rebuilt container: create returns `{endpoint, signingSecret}`. GET, merchant list, admin list-all, status toggle, attempts and DLQ list contain no `secretKey`, `signingSecret`, `whsec_` or the secret value. Toggle off/on still works. A ping to the host mock was signed with a signature that verifies against the one-time secret. The DB value equals the returned secret. No `whsec_` in logs. **Not performed:** the browser UI flow (dialog, copy, refresh). **Left behind:** dev endpoint "step2-verify". **Later, by the owner (in the browser, with webhook.site):** the row shows no secret and no reveal button, and webhook.site received the ping. |
| 2026-11-15 | 3 | Done | **New:** `security/WebhookUrlPolicy`, `security/WebhookSecurityProperties`, `security/UnsafeWebhookUrlException`. **Changed:** `GlobalExceptionHandler` (400 "Invalid Webhook URL"), `WebhookSubscriptionService.createEndpoint` (validate; logs the reason only), `OutboundWebhookDispatcher` (per-attempt policy check → `BLOCKED_DESTINATION`, no retry, no DLQ), `DlqReplayService.replay` (pre-check), `DlqFailureClassifier` (BLOCKED text), `CreateWebhookEndpointRequest` (docs), `application.yml` (`webhook.security.*`), spec + webhook types, console help text. **Local only:** gitignored `docker-compose.override.yml` with the allowlist `host.docker.internal:9090`. **Build:** compiled (container), frontend lint and build passed, no automated tests. **Manually verified** against the rebuilt container: 38 unsafe or invalid URLs all returned 400 with fixed messages. These covered HTTP, loopback, private, CGNAT, metadata, `0.0.0.0`, multicast, IPv6 `::1`/mapped/`fe80`/`fd00`/`ff02`, numeric/octal/hex IP forms, user-info, single-label and `.svc`/`.internal`/`.local` names, public names resolving to loopback (`localtest.me`, `127.0.0.1.nip.io`), an unresolvable host, port 99999, `ftp:`/`javascript:`, underscore hosts, and `host.docker.internal` on non-allowlisted ports. Accepted: `https://webhook.site/…` and `https://example.com/hook` (registration only), and the allowlisted `http://host.docker.internal:9090/ok`, whose ping returned 200. A stored pre-policy raw-IP endpoint (reachable, and created temporarily by SQL) gave a ping result of `BLOCKED_DESTINATION`, with 0 requests reaching the mock. Legacy replay of a DLQ record for it returned FAILED/400: attempts, DLQ and mock counts were unchanged and the record stayed unreplayed. Toggle and the Step 2 secret behaviour are unchanged. The log scan for URLs, IPs and resolver errors found 0 hits. The `aks` profile plus the allowlist makes startup fail (exit 1). The temporary SQL rows were deleted. **Not performed:** a webhook.site ping by me, and the browser UI. **Left behind:** dev endpoints "step3-verify …". **Later, by the owner (browser):** with `http://example.com/webhook`, only the form help text was visible. The 400 error was hidden behind the modal (UI-1); the API rejection itself was re-confirmed by curl. The post-Step-3 webhook.site ping and the local-mock browser checks have not been repeated yet. |
| 2026-11-15 | 4 | In progress | Decision review written: the Section 4 review table, plus a D12 text correction to match Step 3. **No code or configuration changed.** **Findings:** D4 prefix conflicts with the member guide (`sha256=` vs `v1=`). D1/D10 conflict with the guide's "write `dead_letter_webhook_records` + publish to `dlq.webhook.events`". D8 gap: no jobs are created while an endpoint is disabled. D9 limits multiply by 2–8 replicas. D6 field meanings recorded (`estimatedPrice` = creation-time fee estimate). UI-1 recorded. **Waiting:** owner answers on D1–D5, D7–D11 and D13; Member 7 on D6; infra owner on the D11 env var. Section 4 is **not** AGREED. |
| 2026-11-15 | UI-1 (+ Step 4 cont.) | UI-1 fixed in code; Step 4 still in progress | **Changed:** `frontend/src/features/merchant/pages/WebhookResiliencyConsolePage.tsx` only (the in-dialog registration error). **Build:** `npm run lint` and `npm run build` passed; no backend files changed, so no Maven compile; no automated tests. **Verified by me (API only):** `POST` with `http://example.com/webhook` returns 400, and the endpoint row count was unchanged (9/9). **Not performed by me:** any browser check (error visible inside the dialog, valid HTTPS registration, secret dialog, list, toggle, ping). **Step 4:** the review table was rechecked against Section 4 and is still accurate. The D6 question for Member 7 and the D11 "Step 15 only" note are now recorded under Step 4. The decision questionnaire was given to the owner. No decision has been confirmed and Section 4 is **not** AGREED. |
| 2026-11-15 | UI-1 | Done (browser-verified) | Owner browser checks: **(A)** `http://example.com/webhook` shows the real error inside the still-open dialog; **(B)** correcting it to a webhook.site HTTPS URL succeeds and opens the one-time secret dialog; **(C)** listing, Pause/Activate and Test Ping still work. |
| 2026-11-15 | 4 | Complete (D6 provisional) | Owner decisions recorded in Section 4: D1–D5, D7–D11 and D13 **APPROVED**; D6 **PROVISIONAL** (all money fields excluded; Member 7 question kept under Step 4); D12 **IMPLEMENTED**. Conflicts with the guide resolved: D4 `sha256=`; D1/D10 `DEAD` only, no legacy table or broker DLQ for outbound failures. D9 set to 4 per replica, 1 per endpoint, batch 10, 2 s poll, 60 s lease. D8 requires a fan-out change, now recorded under Step 9. Member 9's `WEBHOOK_SECRET_KEY` remains a Step 15 dependency. |
| 2026-11-15 | 5 | Done | **New:** `delivery/WebhookPayloadFactory`, `delivery/PayloadSource`, `dto/outbound/WebhookPayloadV1`, `dto/outbound/ParcelStatusChangedWebhookData`, `dto/outbound/OrderCreatedWebhookData`. **No existing file changed**; no migration, topology, contract or frontend change. **Build:** Maven (JDK 25 container, `-DskipTests compile`) exit 0; frontend unchanged, so no lint or build needed; no automated tests. **Manual runtime check (throwaway jshell script in the JDK 25 container, against the compiled classes; not a project test):** the real dev rows `order.created` 8956add4… and `parcel.status.changed` c9e31eb7… produced the correct envelope, the event's own id, `occurredAt` = stored `occurred_at`, and `trackingNumber` `SD-DEV-0001` from the map (the status event's own value was null). Synthetic rows full of customer id, correlation id, districts, internal sub-status, money and `LKR`, with `traceId`/`newStatus` aliases and an unknown field, produced 0 forbidden-field hits. The map tracking number overrode the event's (`SD-FROM-EVENT`, `SD-6A1B2C3D`). Nanosecond timestamps were truncated to microseconds. Rebuilding gave byte-identical output in every case. An unsupported type and a missing new status were refused. The factory is not wired to anything yet (Step 10). |
| 2026-11-15 | 6 | Done | **New:** `delivery/WebhookSigner`, `delivery/WebhookHeaders`. **Changed:** `docs/api-specs/webhook-resiliency-service.yaml` (`info.description`: merchant signature-verification guide); `IMPLEMENTATION_PLAN.md` (Step 6, the §7.6 snippet). No other Java, no frontend (regenerated types showed only line-ending noise and were restored), no migration, topology or contract change. **Build:** Maven (JDK 25 container, `-DskipTests compile`) exit 0; no automated tests. **Manual runtime check (throwaway jshell in the JDK 25 container; not a project test): 34/34 checks passed.** The known vector equals an independent Python `hmac` result. Repeat is identical. One changed body byte, a changed timestamp, a wrong secret, a tampered body and a modified signature all fail. `v1=`, missing prefix, uppercase, short and non-hex digests are rejected. Null, blank, padded, negative, decimal, suffixed and over-long timestamps are rejected. Exactly ±300 s is accepted and ±301 s rejected. Odd-spaced and non-UTF-8 raw bytes match Python, re-serialised JSON gives a different signature, and the body array is unmutated. The Step 5 payload bytes are unchanged, and their signature verifies end to end and matches Python. **D6** stays provisional (no money fields). **Member 9** (`WEBHOOK_SECRET_KEY`) stays a Step 15 dependency. |
| 2026-11-15 | 7 | Done | **New:** `delivery/WebhookHttpSender`, `delivery/WebhookRequest`, `delivery/AttemptOutcome`, `security/BlockingAddressResolverGroup`, `security/BlockedDestinationException`. **Changed:** `security/WebhookUrlPolicy` (added `isDevAllowlisted`; no rule changed); `application.yml` (`logging.level.reactor.netty.http.client.HttpClientConnect: ERROR`). Step 5/6 files are unchanged, as are `WebClientConfig`, the legacy dispatcher, Test Ping, migrations, topology, contracts and the frontend. **Build:** Maven (JDK 25 container, `-DskipTests compile`) exit 0; no automated tests. **Manual runtime check** (throwaway jshell in the JDK 25 container driving the real sender against an instrumented host mock, plus public TLS test hosts; not a project test). **Outcomes, all per D3:** `/ok` 200 success; 400 permanent; 408 and 425 retryable; 429 retryable with Retry-After `30`; 503 retryable with an HTTP-date Retry-After; 500 retryable; 302 and 307 (Location to metadata) `INVALID_RESPONSE`, with 0 hits on the redirect targets; slow 8 s gives `TIMEOUT` at ≈5.3 s; a 50 MB slow body gave 200 in 85 ms (body not read); connection refused and a DNS failure at connect gave `CONNECTION_FAILURE`; pre-check refusals (http, loopback literal, name resolving to loopback, metadata IP, non-allowlisted port) gave `BLOCKED_DESTINATION`. **Connect-time layer alone** (permissive pre-check plus strict resolver, simulating rebinding): `host.docker.internal` (a private IP) and `localtest.me` were `BLOCKED_DESTINATION`, with 0 requests reaching the mock. **HTTPS:** `example.com` returned 405 (TLS fine); self-signed, wrong-host and expired badssl.com gave `CONNECTION_FAILURE`. **Receiver side, 12/12 requests:** body byte-identical to the signed payload, Content-Length set and not chunked, all D5 headers present, signature equal to an independent Python HMAC with the full `whsec_` secret over `ts.body`, timestamp in Unix seconds, Delivery-Id stable across attempts 1 and 2, `User-Agent: SwiftDrop-Webhooks/1`. **Logs:** the first run showed Reactor Netty WARN lines with hosts and TLS text, which the logger-level change fixed (re-run: 0 hits); the sender's own lines contain no URL, secret, signature, body or exception text. **Container** rebuilt and healthy (Spring wiring and YAML OK); legacy Test Ping unchanged (`/ok` 200, `/boom` 500, still millisecond timestamp and legacy headers). **Open for Step 8:** `UNKNOWN` failures are marked retryable (D3 does not list them; confirm). |
| 2026-11-15 | 8 | Done (policy level) | **New:** `delivery/DeliveryRetryPolicy`, `delivery/RetryDecision`, `delivery/DeliveryProperties`. **Changed:** `application.yml` (`webhook.delivery.*`); plan D3 row (UNKNOWN retryable, approved). Step 5–7 files, schema, migrations, topology, contracts and frontend unchanged; no migration needed. **Build:** Maven (JDK 25 container, `-DskipTests compile`) exit 0; container rebuilt and healthy (properties bound from YAML); no automated tests. **Manual policy check (throwaway jshell, fixed clock, controllable random; not a project test): 61/61 passed.**<br>- 200/204 succeed.<br>- 408/425/429/500/502/503/504, connection failure, timeout and UNKNOWN retry.<br>- 301/302/307/400/401/403/404/409/410/422 and BLOCKED are DEAD(permanent) on attempt 1; a contradictory `retryable=true` flag cannot override a permanent class.<br>- A simulated job with four 500s gives exactly 4 sends: RETRY+30s, RETRY+120s, RETRY+600s, then DEAD(exhausted). Attempts 5/9/100 are always DEAD; success on attempt 4 is SUCCEEDED.<br>- Jitter over 20k samples each: attempt 1 [24001, 36000] ms, attempt 2 [96006, 143998] ms, attempt 3 [480003, 719934] ms. Exact extremes: random 0 gives 24 s, 0.5 gives 30 s.<br>- Retry-After: `90`, IMF-fixdate, RFC 850 and asctime (+90 s) all give 90 s. `45` gives 45 s (used); `5` gives 30 s (never shortens); 86400, a 25-digit value and a 2031 date give 600 s (capped); attempt-3 jittered 720 s beats a capped 600 s; ignored for 500; a 429 on attempt 4 is still DEAD.<br>- 14 invalid or past values (blank, `abc`, `-5`, `+5`, `1.5`, `0`, `30s`, a past date, an invalid date, PST, ISO) all fall back to 30 s.<br>- 8 threads × 20k concurrent decisions: never RETRY at or after attempt 4.<br>**Not verifiable before Steps 9–10:** persisted state transitions, attempt-row history, lease and concurrency fencing in the DB, and end-to-end `next_attempt_at` gaps. |
| 2026-11-15 | 9 | Done (no migration) | **New:** `domain/repository/DeliveryJobRepository`, `delivery/DeliveryAttemptRecorder`, `delivery/ClaimedDelivery`. **Changed:** `service/ingestion/DeliveryFanOut` (paused endpoints included, D8). Step 5–8 files, V1–V3, topology, contracts and frontend unchanged. The planned V4 was not needed (reasons under Step 9); the replay index moved to Step 13. **Build:** Maven (JDK 25 container, `-DskipTests compile`) exit 0; container rebuilt and healthy (Spring wiring OK); no automated tests.<br>**Persistence check (throwaway jshell with real Spring `@Transactional` proxies against an isolated scratch DB `swiftdrop_webhook_step9`, Flyway-migrated from V1–V3, dropped afterwards; not a project test): 29/29.**<br>- 200 gives SUCCEEDED (next and lease cleared, 1 row).<br>- 503 + Retry-After 45 gives RETRY at about +44 s; it isn't claimable before then. Attempts 2 and 3 give RETRY at +2:09 and +10:12 (within ±20 %). Attempt 4 gives DEAD with rows 1..4. A job forced back to RETRY at count 4 can't be claimed. 404 is DEAD at once.<br>- A forced attempt-insert failure left the job IN_FLIGHT with 0 rows (both rolled back). A pre-existing row for the same attempt number made the unique index reject the insert and roll back the transition.<br>- Lease expiry recovery gives RETRY plus an `UNKNOWN` row. After a re-claim by w2, the stale w1 result is ignored; w2 then records normally (rows 1:UNKNOWN, 2:OK).<br>- Late results for SUCCEEDED and DEAD jobs are ignored.<br>- Two concurrent records of one attempt: exactly one applied, one row. 6 concurrent claimers on 40 jobs: 40 distinct claims. 8 rounds × 4 concurrent workers always failing: 0 jobs over 4 attempts, 0 attempt_count/row mismatches, 0 duplicate rows, 50/50 jobs DEAD at exactly 4.<br>- A paused job isn't claimable; after reactivation the same job is claimed with no new rows.<br>- FIFO: only the older job of an order is claimable; the newer stays blocked while the older is RETRY and is unblocked once it is DEAD.<br>- A cross-merchant delivery is rejected by `fk_webhook_deliveries_endpoint_merchant`; attempt rows always carry their delivery's merchant and endpoint; no legacy DLQ rows.<br>**Fan-out check (real pipeline: RabbitMQ to the running service to the dev DB):** with endpoints step9 active / paused / order.created-only (merchant …a1) and another merchant (…a2), the status event created 12 PENDING jobs = all 12 matching …a1 endpoints, **including the paused one**. The order-only endpoint and the other merchant got none; the order-only endpoint got its order.created job; 0 attempt rows (nothing sent). A re-published duplicate eventId left 16/16 jobs and 1 inbox row. A read-only eligibility query gave 0 for the paused job while paused and 1 after reactivation, with no duplicate job. DLQ table 0, broker DLQ 0, no sender or recorder log lines.<br>**Not verifiable before Step 10:** end-to-end sends driven by a worker, real lease expiry under load, and multi-replica behaviour beyond the in-process concurrency above. |
| 2026-11-15 | 10 | Done | **New:** `delivery/DeliveryWorker`, `delivery/DeliveryProcessor`, `delivery/LeaseRecoveryJob`, `config/DeliverySchedulingConfig`. **Changed:** `DeliveryJobRepository` (+`releaseClaim`), `DeliveryAttemptRecorder` (+`release`), `application.yml` (`worker-enabled`, `lease-recovery-interval`), plan (Step 10, §7.7). Step 5–8 code, sender, topology, contracts and frontend unchanged; no migration. **Build:** Maven (JDK 25 container, `-DskipTests compile`) exit 0; no automated tests.<br>**Phase 1, isolated end to end** (a second container from the same image on a scratch DB, RabbitMQ deliberately unreachable, retry delays 3s/5s/8s, lease 15 s, recovery every 5 s, host mock via the dev allowlist; scratch DB and container removed afterwards): **15/15 scenarios passed** (one scenario first failed because of a test-design error, a slow mock longer than the 5 s response timeout; re-run with 4 s, it passed).<br>- Claimed and sent: SUCCEEDED with 1 row. One POST arrived, body byte-identical to an independently built payload, all D5 headers present, signature equal to an independent HMAC with the full `whsec_` secret.<br>- 400: DEAD after 1 attempt. 500 ×4: DEAD at 4, same Delivery-Id, Attempt headers 1..4, gaps 4.2/6.0/10.1 s (never early). 429 + Retry-After 10: next attempt after 12.2 s.<br>- Paused: 0 requests and PENDING/0; after reactivation delivered once with no new job. Pause between claim and send (held by a table lock): no request, claim released to PENDING/0, no row.<br>- FIFO: the older DEAD job didn't block the newer; the older RETRY job ran all 4 attempts before the newer's first.<br>- 9 slow jobs: peak 4 in flight, ≤1 per endpoint. Container `docker kill`ed mid-attempt and restarted: recovered as UNKNOWN, then SUCCEEDED (rows UNKNOWN, OK; the receiver saw 2 requests, at-least-once).<br>- A processing exception (missing mapping): no request, 4 UNKNOWN recovery rows, then DEAD, while other jobs kept flowing.<br>- Logs: 0 secrets, URLs, response bodies or signatures; the only exceptions came from the deliberately absent RabbitMQ. 0 legacy DLQ rows.<br>**Phase 2, real dev environment:** the owner's 3 endpoints delivered their 10-job backlog to webhook.site on first start (all 200). My step1–9 verification endpoints were **paused first** to avoid third-party traffic. Real `order.created` and `parcel.status.changed` published through RabbitMQ reached a fresh mock endpoint (2 requests, both signatures valid with its one-time secret; the money and district fields published in `order.created` were absent). Paused endpoints got 10 jobs, all still PENDING/0. Test Ping is unchanged (millisecond timestamp, legacy User-Agent, no Event-Id). Legacy DLQ 0, broker DLQ 0, logs clean. **Not verified end to end:** a late result from a still-running stale worker (covered at persistence level in Step 9), and multiple real replicas. **Left in dev DB:** endpoints step1–10 "…-verify" (paused) with PENDING jobs. |
| 2026-11-15 | End-of-session security review | Done | **Disabled** the dev SSRF exception: `docker-compose.override.yml` (gitignored, kept) now sets `WEBHOOK_SECURITY_DEV_HTTP_ALLOWLIST: ""`, with enable/disable instructions in its comments; the webhook service was recreated (data kept) and is healthy. **Verified:** the env is empty in the container; no "DEVELOPMENT ONLY" warning; `http://host.docker.internal:9090/ok` and 8 other unsafe URLs return 400 with no rows created. Production builds (modes `production` and `webhook-dev`) contain 0 occurrences of `dev-token`, the bypass variables or the dev identity UUIDs. `vite preview --mode webhook-dev` does not proxy to the service (it went to the gateway and was refused). The running dev server ignored forged `SYSTEM_ADMIN` and merchant headers (returned only merchant …a1's 14 endpoints, not all 15). The service logs have 0 secrets, signatures, URLs or response bodies. Worker settings unchanged (4/1/10/2s/60s). No tracked file contains a secret. **Unchanged by design:** the frontend dev bypass (dev-server-only), and all production controls. **Not changed:** credentials, secrets, DB data, volumes, topology. |
| 2026-11-15 | CI fix | Done | The PR's CI (`mvn -B -f backend/pom.xml verify -Dspring.profiles.active=integration-test`) failed only in this module: `maven-failsafe-plugin:3.5.1:integration-test … No tests to run!`, caused by `<failIfNoTests>true</failIfNoTests>` in `webhook-resiliency-service/pom.xml` after the Step 0 test removal. Changed it to `false` (with a comment explaining when to restore `true`). **Verified:** in the JDK 25 container, `mvn -pl webhook-resiliency-service -am verify -Dspring.profiles.active=integration-test -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` ran failsafe `integration-test` and `verify` for this module and gave BUILD SUCCESS, with no tests executed. The full CI run was not re-run locally. Notification and AnalyticsAI were skipped by the failed CI run and are first exercised on the next run; they don't have this problem (AnalyticsAI keeps its guard but has an IT; Notification declares no failsafe). |
| 2026-11-27 | Audit after team PRs #87–#205 | Done (docs only) | **Changed:** this plan (test rule, Section 2a, Steps 11/12/17/18 status, D6 note, remaining work R0–R7, Section 6 rows, Section 7 note). **New:** `docs/LOCAL-TESTING.md`. No code, test, migration, config or frontend change. **Automated:** `mvn -pl webhook-resiliency-service -am verify -Dspring.profiles.active=integration-test` in the JDK 25 container with the Docker socket: BUILD SUCCESS; 187 unit tests and 43 ITs (`FailedDeliveryReplayIT` 6, `WebhookDeliveryFlowIT` 8, `WebhookIngestionIT` 18, `WebhookSchemaMigrationIT` 11), 0 failures; JaCoCo line 88.2 %, branch 72.9 % (gate 85 %/68 % met; margins 3.2 and 4.9 points). **Manual (local broker, merchant `…c1` with no endpoints, so nothing was sent):** shop order (`customerId = merchantId`) RESOLVED; a customer booking naming the shop NO_MERCHANT; legacy order without `customerId` RESOLVED; `CREATED` and `PICKUP_ASSIGNED` RESOLVED; `IN_TRANSIT → IN_TRANSIT` and `CANCELLED → CANCELLED` NO_CHANGE; duplicate event DUPLICATE; early status AWAITING_MERCHANT then released. **Left behind:** 8 inbox rows with `source_service = 'manual-test'` (merchant `…c1` / none), no deliveries. **Not run:** Method B (real lifecycle). **Findings:** N1–N8 in Section 2a. |
| 2026-11-27 | 17 | Done | **Deleted:** `service/OutboundWebhookDispatcher`, `resiliency/HmacSignatureGenerator`, `resiliency/ExponentialBackoffScheduler`, `config/WebClientConfig` (its `WebClient` bean served only the dispatcher), `dto/ReplayDlqResponse`; tests `HmacSignatureGeneratorTest`, `ExponentialBackoffSchedulerTest` (their classes are gone). **New:** `exception/LegacyReplayRetiredException` (410 handler in `GlobalExceptionHandler`). **Changed:** `DlqReplayController` (replay and replay-all throw the 410 exception before any service call; list/get unchanged paths), `DlqReplayService` (read-only: list/get only, readers MERCHANT and SYSTEM_ADMIN; the DISPATCHER metadata view removed; sole dependency the legacy repository), `DlqFailureClassifier` (unused `storedReason` writer removed; parsing of stored legacy text kept), Javadoc in `DeadLetterWebhookRecord`, `DeadLetterWebhookResponse`, `WebhookHttpSender`; spec `docs/api-specs/webhook-resiliency-service.yaml` (replay operations deprecated, 410 `Gone` response, `ReplayDlqResponse` schemas removed, DISPATCHER wording removed); regenerated `frontend/src/types/generated/webhook-resiliency-service.ts` only (openapi-typescript on this one spec); `docs/LOCAL-TESTING.md` §2.6. **Not changed:** migrations, the `dead_letter_webhook_records` table and rows, RabbitMQ topology and the inbound `dlq.webhook.events.queue`, the new sender/signer/worker/retry/replay code, contracts, other services. **Tests:** `DlqReplayServiceTest` (DISPATCHER/CUSTOMER/COURIER refused; the service holds only the legacy repository and exposes only list/get; reads never save), `DlqReplayControllerTest` (410 body for MERCHANT, SYSTEM_ADMIN and DISPATCHER on both replay routes with no service call; 401 without identity; DISPATCHER refusal mapped to 403), `WebhookDeliveryFlowIT.legacyDeadLetterRecordsAreReadOnly` (replaces `legacyDeadLetterReplay`: owner list/get, stranger 404/empty, DISPATCHER 403, admin 200, both replays 410, no request reaches the receiver, the record stays unreplayed, no attempt rows), `failedDeliveryIsRetriedListedAndReplayed` (+ same Event-Id / new Delivery-Id on replay, 0 legacy rows after a DEAD delivery), `endpointManagement` (+ Test Ping is `webhook.test.ping`, attempt 1, signature equals `WebhookSigner` over timestamp and body, apiVersion 1, no `webhook_deliveries` row). **Build:** `mvn -B -pl webhook-resiliency-service -am verify -Dspring.profiles.active=integration-test` in `maven:3-eclipse-temurin-25` with the Docker socket: first run 1 error (my new IT used column `replayed`; the column is `is_replayed`), fixed; second run BUILD SUCCESS, 172 unit tests + 43 ITs, 0 failures; JaCoCo line 88.8 %, branch 74.3 % (gate 85/68 met; before: 88.2/72.9). Frontend `npm run lint` (type-check) passed. **Manual (rebuilt local container, healthy, worker started):** `GET /dlq` MERCHANT 200, SYSTEM_ADMIN 200, DISPATCHER 403 (list and get); `POST /dlq/{id}/replay` MERCHANT 410, `POST /dlq/replay-all` SYSTEM_ADMIN 410 (fixed message naming the new API), no identity 401; `GET /deliveries/failed` 200, `POST /deliveries/failed/replay` 202, unknown id 404; legacy rows 0 → 0 and attempt rows 51 → 51; the jar contains none of the deleted classes; `webhook.events.queue` and `dlq.webhook.events.queue` unchanged (same arguments, 2 consumers). **Not performed manually:** a live Test Ping or delivery to a receiver (the dev allowlist is off and no third-party endpoint was used); covered by `WebhookDeliveryFlowIT`. **Limitation:** hosted `dead_letter_webhook_records` count unknown; those rows stay visible read-only. |
| 2026-11-27 | R3 (test hardening) | Done | **Tests only; no production change.** **New:** `DeliveryLeaseFencingIT` (18, real PostgreSQL 16 via Testcontainers, the real `DeliveryJobRepository` SQL and `DeliveryAttemptRecorder`; time relative to the database's `now()`, no sleeps): **A** recovery of an expired lease → RETRY with exactly the 30 s delay, one `UNKNOWN` attempt row (no status, no latency); expiry on the last attempt → DEAD; a lease 1 s from expiry untouched and its holder still records; repeated runs recover nothing more; SUCCEEDED/DEAD/PENDING rows never touched; a second recovery run skips a row locked by another run's open transaction (SKIP LOCKED), and the row is recovered exactly once later. **B** after recovery + re-claim by w2, w1's late success and late failures are rejected with the persisted row byte-identical (status, owner, attempt, lease, schedule, version, updated_at) and no attempt row; the same owner's result for an older attempt number rejected; results after SUCCEEDED or after recovery rejected. **C** release of attempt 1 → PENDING, count 0, lease cleared, `next_attempt_at` unchanged, no attempt row, re-claimable as attempt 1; release of attempt 2 → RETRY, count 1; another owner, a wrong attempt number, a stale claim after re-claim, and claims of SUCCEEDED/DEAD/recovered rows cannot release (row unchanged). **D** a claimer holding rows in an open transaction and a second claimer get disjoint sets covering all jobs; a locked oldest job still blocks its order's next job for the other claimer; 5 concurrent workers (barrier start) over 6 orders × 4 events: every job claimed once, attempt 1, SUCCEEDED, no claim while an older job of the same endpoint+order was unfinished, per-order claims oldest first. `DeliveryWorkerLimitsTest` (4, unit, processing held on a latch): second job of a busy endpoint handed back unsent while another endpoint runs; claim size = min(batch, free capacity) and no claim while full; excess claimed jobs handed back; a processing exception frees the permits and leaves the lease for recovery (not released). **E** `ConnectTimeAddressGuardTest` (24, unit) with a test-only JDK `InetAddressResolverProvider` (`TestDnsResolverProvider` + `src/test/resources/META-INF/services`; only names ending `.r3.swiftdrop-test`, everything else goes to the JDK resolver): the real `BlockingAddressResolverGroup` returns public answers (IPv4 + IPv6, port kept); refuses loopback, private, CGNAT, link-local/metadata, ULA, IPv4-mapped loopback, any-local and multicast answers (13 cases, both `resolve` and `resolveAll`); one blocked address anywhere among public ones refuses the name; an unresolvable name fails closed; already-resolved addresses (IP literals, a public-looking name with a private address) are checked; the dev exception is exact host:port. Through the real `WebhookHttpSender`: a name that passes URL validation but resolves to loopback at connect time → `BLOCKED_DESTINATION`, no request at the loopback receiver; control with a permitting resolver reaches it (200); IP-literal URL refused at connect time; unresolvable at connect time → `CONNECTION_FAILURE`, nothing sent; production two-argument constructor refuses a private name. **Changed:** `DlqReplayServiceTest.legacyServiceCannotSend` (same assertions, rewritten with explicit types: an IDE's Eclipse compiler wrote a broken class for the previous form into `target/`, which Maven then ran; `clean verify` used). **Build:** `mvn -B -pl webhook-resiliency-service -am clean verify -Dspring.profiles.active=integration-test` (JDK 25 container, Docker socket): BUILD SUCCESS, 200 unit + 61 IT, 0 failures/errors/skipped; JaCoCo line **93.1 %**, branch **80.2 %** (gate 85/68; before 88.8/74.3). **Not covered:** the exact `lease_expires_at = now()` boundary (both sides use the database clock in separate transactions); recovery of a worker killed mid-HTTP in a real process; response timeouts and Retry-After through the sender; DNS rebinding where the *same* name answers differently on two lookups (the JDK caches answers for 30 s, so it is modelled by a validation that passes and a strict connect-time resolver); multi-replica limits (per-replica semaphores are tested in-process). |
| 2026-11-27 | R1 (API guide accuracy) | Done (docs only) | **Changed:** `frontend/src/features/merchant/pages/ApiGuidePage.tsx` (Receive updates tab): removed the false promise "Events of one parcel arrive in order" (a replay, or an event received late, can arrive after a newer one: compare `occurredAt`); added that a status event is sent only when the public status changes and can go back (`OUT_FOR_DELIVERY` → `IN_TRANSIT`); `failureReason`, when present; Delivery-Id is the same in every retry; a test ping is sent once and never retried; `Retry-After` on 429/503 can lengthen a wait to 10 minutes; a paused address keeps its events and gets them after reactivation; a replay has the same Event-Id and can be repeated only if it failed. `docs/api-specs/webhook-resiliency-service.yaml`: "exponential" retries → scheduled; new "Delivery behaviour" section with the same facts; pause semantics on `PATCH /subscriptions/{id}/status`; replay-all summary corrected (it also re-queues deliveries whose last replay failed). Regenerated `frontend/src/types/generated/webhook-resiliency-service.ts` (description comments only). `docs/LOCAL-TESTING.md` replay caveat. **Evidence:** `DeliveryJobRepository.claimDue` (FIFO, DEAD/SUCCEEDED do not block), `FailedDeliveryRepository.queueReplays` (NOT EXISTS non-DEAD replay), `InboundEventIngestionService` (NO_CHANGE), `DeliveryExecutionService`/`HubDeskService` (hand-in from OUT_FOR_DELIVERY), `DeliveryRetryPolicy` + `DeliveryRetryPolicyTest` (schedule, Retry-After cap), `WebhookHttpSender` (5 s response timeout, no redirects), `DeliveryFanOut` + `WebhookDeliveryFlowIT.endpointManagement` (paused endpoint keeps events), `TestPingService` + `TestPingServiceTest`, `ReturnRoute` (FAILED from ASSIGNED needs no reason). **No change:** production code, API behaviour, payloads, signing, the legacy DLQ (not mentioned in the merchant guide; already described as read-only/410 in the spec since Step 17). **Verification:** see the R1 report. **Open:** N1 (order-service cancellations never become webhooks, Member 3) — the guide still lists `CANCELLED`, which is correct for delivery-service cancellations only. |
