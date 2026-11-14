# Webhook Delivery & Resiliency Service — Implementation Plan (Member 8)

This is the working reference for all Member 8 implementation work. It records what exists, what is
missing, the agreed order of work, cross-member dependencies, and how to run and verify things locally.

- **Service path:** `backend/webhook-resiliency-service`. Below, `WH/` means `src/main/java/com/swiftdrop/webhook/`.
- **Shared contracts path:** `CC/` means `backend/common-contracts/src/main/java/com/swiftdrop/contracts/`.
- **Status:** written on 2026-11-14, after the technical audit and the "plan + dev-mode" setup task (Step 0).

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

### NO AUTOMATED TESTS IN THIS DEVELOPMENT SESSION

On 2026-11-14 the service owner decided the following:

- **Removed:** all test sources under `backend/webhook-resiliency-service/src/test` were deleted (see Step 0).
- **Do not create tests.** No unit, integration, MVC, HTTP, Testcontainers or end-to-end tests are to be written in any later step of this development session, and no roadmap step includes test work.
- **Do instead:** verify by compilation (`-DskipTests`), static reading of the change, and the **manual verification** listed in each step.
- **Never claim tested.** Do not say something was "tested" when it was only compiled. Say "compiled" or "manually verified (how)".
- **Lost coverage.** The deleted tests covered ingestion deduplication, merchant resolution, the status-before-`order.created` race, ingestion DLQ behaviour, broker/`definitions.json` parity and schema constraints. That regression protection is gone, so [Section 1](#1-preserve-these-working-components) components must be changed with extra care. They are best not changed at all.
- **Shared test infrastructure is untouched.** Test dependencies in `pom.xml` (Testcontainers etc.) were left in place. They are harmless with no test sources.

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
| API paths | `/api/v1/webhooks/subscriptions/**`, `/api/v1/webhooks/dlq/**` | Used by the frontend (`frontend/src/services/api/webhookApi.ts`) and routed by the gateway. |
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

Also note:
- `OutboundWebhookDispatcher` is `@Deprecated`. Only test ping and legacy replay use it.
- `webhook.dispatch.requested` is bound but has no producer. The consumer deliberately rejects it to the DLQ.
- The gateway does not route the alias paths `/api/v1/dlq-replay` and `/api/v1/webhook-subscription`.

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

## 4. Decisions to confirm before the delivery pipeline (Step 4)

These are proposed defaults. Steps 5–10 assume them. Change them here, not silently in code.

| # | Decision | Proposed default |
|---|---|---|
| D1 | Retry schedule | Attempt 1 immediately. Retries after 30 s, 2 min and 10 min (from `docs/member-guides/member-8.md`), so 4 attempts in total, then `DEAD`. Jitter ±20 %. |
| D2 | Retry-After | Honoured for 429 and 503 (seconds or HTTP-date), capped at 10 min. It never shortens the schedule. |
| D3 | Classification | **Success:** 2xx. **Transient:** timeout, connection failure, 408, 425, 429, 5xx. **Permanent, goes straight to DEAD:** 3xx (`INVALID_RESPONSE`), other 4xx (`HTTP_CLIENT_ERROR`), blocked destination (`BLOCKED_DESTINATION`). The values already exist in `AttemptErrorClass`. |
| D4 | Signature v1 | `X-SwiftDrop-Signature: v1=<hex HMAC-SHA256(secret, "<timestamp>.<raw body bytes>")>`. `X-SwiftDrop-Timestamp` is Unix **seconds**. Merchants should reject requests older than 5 min. |
| D5 | Headers | `X-SwiftDrop-Event-Id` (inbound eventId, stable). `X-SwiftDrop-Delivery-Id` (delivery_id, stable across retries of the same job). `X-SwiftDrop-Event` (event type). `X-SwiftDrop-Attempt`. `User-Agent: SwiftDrop-Webhooks/1`. |
| D6 | Payload v1 | `{"id","type","apiVersion":1,"occurredAt","data":{"orderId","merchantOrderReference" (null until Step 18),"trackingNumber" (from order_merchant_map),"status","previousStatus","failureReason"}}`. No `customerId`, `internalSubStatus`, districts or correlationId. For `order.created`: orderId, trackingNumber, speed, codAmount, declaredValue, estimatedPrice, currency. Confirm which money fields a merchant should see. |
| D7 | Ordering | Per (endpoint, order) FIFO. A job is not claimed while an older non-terminal job exists for the same endpoint and order. `DEAD` and `SUCCEEDED` do not block. Different parcels never block each other. |
| D8 | Disabled endpoint | Its pending jobs are skipped by the claim query (they stay `PENDING`) and resume when it is re-enabled. Deleting an endpoint is already blocked by the FK (409). |
| D9 | Concurrency | Per replica: global limit 8 in-flight requests, per-endpoint limit 2. Claim batch 20. Poll every 2 s. Lease 60 s. |
| D10 | Replay | A new `webhook_deliveries` row with `replay_of_delivery_id`, the **same event id** and a new delivery id. Asynchronous (202), and only one active replay per original. |
| D11 | Secrets | Shown once (create and rotate). Stored AES-GCM encrypted with env `WEBHOOK_SECRET_KEY` (name from the member guide). Rotation replaces the secret immediately. A grace period with the old secret is deferred. |
| D12 | SSRF dev exception | A property-driven allowlist of hosts (for example `host.docker.internal`, `localhost`). It is empty by default and set only in local configuration. |
| D13 | Is `order.created` merchant-facing? | Yes: merchants may subscribe to it (that is already the case today). |

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
- **Status:** NOT STARTED

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
- **Status:** NOT STARTED

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
- **Status:** NOT STARTED

### Step 4 — Confirm delivery decisions (no code)
- **Objective:** the owner confirms or edits the table in [Section 4](#4-decisions-to-confirm-before-the-delivery-pipeline-step-4) and marks it **AGREED**.
- **Why:** Steps 5–10 encode these rules, and changing them later causes rework and merchant-visible contract churn.
- **Other members:** Member 7 for D6 (whether merchants should see money fields).
- **Done when:** Section 4 is marked AGREED with any edits.
- **Status:** NOT STARTED

### Step 5 — Outbound payload v1 (pure code)
- **Objective:** build deterministic public JSON bytes from a delivery's inbound event.
- **Why:** today the inbox stores the internal contract. Merchants need a stable, minimal, versioned payload without PII (D6).
- **Inspect:** `domain/entity/InboundEvent` (payload JSONB), `OrderMerchantMapping` (authoritative `tracking_number`), `InboundEventDecoder` (stored field names).
- **New files:** `WH/delivery/WebhookPayloadFactory.java`, `WH/dto/outbound/*` records.
- **Preserve:** the inbox format. Do not change what ingestion stores.
- **Depends on:** Step 4. **Other members:** none (Step 18 adds the reference later).
- **Done when:** compiled; the factory is a Spring bean with no I/O except reading passed-in entities.
- **Manual check:** temporarily log the bytes in Step 10 or call the factory from a debugger. Confirm there is no `customerId` and that the tracking number comes from the map. Keep the check light; full verification happens in Step 10.
- **Status:** NOT STARTED

### Step 6 — Signature v1 (pure code)
- **Objective:** sign `timestamp + "." + body` (D4). Provide a constant-time verify helper and the header-name constants (D5).
- **Why:** H4.
- **Inspect / modify:** `WH/resiliency/HmacSignatureGenerator` (keep it for legacy use until Step 17, or extend it).
- **New files:** `WH/delivery/WebhookSigner.java`, `WH/delivery/WebhookHeaders.java`.
- **Depends on:** Step 4.
- **Done when:** compiled; verification uses `MessageDigest.isEqual`.
- **Manual check:** in Step 10, recompute the signature from a received request in PowerShell or Python with the endpoint secret and compare (a script is in Section 7.6).
- **Status:** NOT STARTED

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
- **Status:** NOT STARTED

### Step 8 — Retry policy (pure code)
- **Objective:** given an attempt number and an outcome, return a decision: `SUCCEEDED`, or `RETRY` at a computed time (schedule D1, jitter, Retry-After cap D2), or `DEAD`.
- **Inspect:** `resiliency/ExponentialBackoffScheduler`. It is not reused: its fixed 2 s/30 s schedule conflicts with D1. It is deleted in Step 17.
- **New files:** `WH/delivery/DeliveryRetryPolicy.java`, `WH/delivery/DeliveryProperties.java` (`webhook.delivery.*`: schedule, jitter, caps, D9 limits).
- **Depends on:** Step 4.
- **Done when:** compiled; a `Clock` and a random source are injected so the behaviour is deterministic when needed.
- **Manual check:** in Step 10, the `next_attempt_at` gaps in the DB match the schedule within ±20 %.
- **Status:** NOT STARTED

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
- **Must also update** `IngestionJdbcRepository.insertDeliveryIfAbsent` to fill the new columns. This is the **only** ingestion change allowed: one insert statement. Check the V3 check constraints (`ck_webhook_deliveries_*`) before writing the SQL.
- **Preserve:** V1–V3 unchanged, and all other ingestion code.
- **Depends on:** Step 4.
- **Done when:** compiled; the service starts and Flyway applies V4 to the existing local DB without errors. Check this with `docker logs`.
- **Manual check:** `\d webhook_deliveries` shows the new columns. Existing rows are backfilled: `SELECT count(*) FROM webhook_deliveries WHERE order_id IS NULL;` returns 0.
- **Status:** NOT STARTED

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
- **Status:** NOT STARTED

### Step 11 — Move test ping onto the new sender
- **Objective:** ping uses payload, signer and sender once, synchronously, with no retry and no DB job. It records a legacy-shape attempt or none.
- **Why:** pings are then signed exactly like real deliveries, and the legacy dispatcher loses its last ping usage.
- **Modify:** `WebhookSubscriptionService.testPing`.
- **Depends on:** Steps 1, 3, 5, 6 and 7.
- **Done when:** compiled; the ping signature verifies with the Section 7.6 script.
- **Status:** NOT STARTED

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
- **Status:** NOT STARTED

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
- **Status:** NOT STARTED

### Step 14 — Frontend console update
- **Objective:**
  - Delivery history table with an attempts drawer.
  - DEAD list with a replay button.
  - Remove the legacy replay buttons.
  - Update the copy ("3-attempt exponential backoff" becomes the real D1 schedule).
- **Modify:** `frontend/src/features/merchant/pages/WebhookResiliencyConsolePage.tsx`, `components/WebhookDlqReplayTable.tsx`, `services/api/webhookApi.ts` (generated types only).
- **Optional new file:** `WebhookSettingsCard.tsx` (named in the member guide).
- **Rules:** `frontend/AGENTS.md`: Velocity design-system components, compact density, and generated types only.
- **Split if large:** 14a history; 14b DEAD/replay.
- **Depends on:** Steps 12 and 13.
- **Done when:** `npm run lint && npm run build` pass, and the flows work in dev mode.
- **Status:** NOT STARTED

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
- **Status:** NOT STARTED

### Step 18 — Merchant order reference (coordinated; blocked on Member 3)
- **Objective:** merchants can correlate a webhook with their own order.
- **Changes:**
  - After Member 3 publishes an optional `externalRef` on `OrderCreatedEvent` (with a back-compatible constructor), `V6` adds `merchant_order_ref` to `order_merchant_map`.
  - The decoder and `insertOrderOwnershipIfAbsent` store it. This is a minimal ingestion change.
  - The payload's `merchantOrderReference` gets filled.
- **Depends on:** Step 5 plus the Member 3 / contracts change. **Do not edit `CC/` yourself.**
- **Status:** BLOCKED (waiting for Member 3)

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
| A customer can set any `merchantId` on `POST /api/v1/orders`: `CreateOrderRequest.merchantId` flows into `OrderCreatedEvent.merchantId` | **Member 3** | First-claim-wins ownership sends that order's events (COD, values, statuses) to the victim merchant. This cannot be detected downstream. | Ignore or reject `merchantId` on the public customer endpoint. Set it only on the internal merchant path (`/internal/v1/orders`). | Production use. Not a code dependency of any step. |
| `order.created` and `parcel.status.changed` are published inside `@Transactional` (`CustomerOrderService.persistNewOrder`, cancel paths) | **Member 3** | A rollback (for example losing the `(merchantId, externalRef)` race) still emits events, so the merchant gets webhooks for a phantom order | Publish after commit (`TransactionSynchronization` / `@TransactionalEventListener`) | Data quality from Step 10 on |
| No merchant order reference in events | **Member 3** + contracts owner (+ Member 7 to confirm the field name) | The merchant cannot match a webhook to its own order | Add an optional `externalRef` to `CC/events/order/OrderCreatedEvent` with a back-compatible constructor. Call sites: customer-order `OrderEventPublisher`, dispatch `OrderCreatedConsumerIT`, `CourierMatchingEngineTest`. | Step 18 |
| delivery-execution makes its own tracking number, `"SD-" + orderId[0..8]` (`DeliveryExecutionService.handleCourierAssigned`) | **Member 6** | The merchant would see two tracking numbers. Mitigation in this service: the payload uses `order_merchant_map.tracking_number` (Step 5). | Use `CourierAssignedEvent.trackingNumber` | None (mitigated) |
| A random correlationId on every delivery-execution event (`PublishStateTransitionAction.doPublish`) | **Member 6** | Traces break between the request and the webhook | Propagate the incoming correlationId | None (only affects logs) |
| `PICKUP_ASSIGNED` is likely published by both dispatch (`CourierMatchingEngine`) and delivery-execution (unverified at runtime) | **Members 5 + 6** | Merchants get two PICKUP_ASSIGNED webhooks with different event ids | Agree on one owner | None. Dedupe is not possible here because the event ids differ. |
| `MerchantEventPublisher.publishOrderCreated` (merchant-b2b) has no callers | **Member 7** | If it is wired up, a second ownership claim appears: a disagreement goes to the DLQ, an agreement is harmless | Remove it or document that it must not be used | None |
| `merchantId` JWT claim for approved merchants | **Member 2 / Member 7** | `MerchantAccess` needs `X-Merchant-Id` for MERCHANT callers | Confirm the claim is issued after approval | Real (non-dev) merchant use |
| `WEBHOOK_SECRET_KEY` in compose, `.env.example`, Helm and secret scripts | **Infra owner (Member 9)** | Required for Step 15 | Agree on a name and add mirrored config | Step 15 |
| The dev SSRF allowlist reaching the container | **Infra owner** (if compose changes) | Local ping and delivery to the host mock | Choose an env line or an untracked override | Step 3 local verification |
| `definitions.json` | **Member 9** | Topology parity | No change needed. The member guide's "broker config drift" item (`docs/member-guides/member-8.md`) is **outdated**: the files match. | — |

---

## 7. Local development and manual verification

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
- **Target URL:** the service runs in Docker, so a mock on the host is reached through `host.docker.internal`, not `localhost`. After Step 3, that host must be on the dev allowlist.

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

**Signature check, after Step 6.** Recompute the HMAC with the endpoint secret and compare it with the received header:

```python
import hmac, hashlib
expected = hmac.new(SECRET.encode(), (TIMESTAMP + '.').encode() + BODY_BYTES, hashlib.sha256).hexdigest()
```

---

## Progress log

Append one entry per completed task. Never mark a step done unless its "Done when" criteria were met.

| Date | Step | Result | Notes |
|---|---|---|---|
| 2026-11-14 | 0 | Done | Plan created. Deleted 11 test files in `src/test`. Frontend dev mode added (`frontend/.env.webhook-dev`, `vite.config.ts`, `src/app/devAuthBypass.ts`, `src/main.tsx`, `package.json` script `dev:webhook`). Fixed the README broker diagram. Backend compiled in the JDK 25 Maven container; frontend lint and build passed. No backend production code changed. |
