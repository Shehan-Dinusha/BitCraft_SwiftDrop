# Webhook service: local testing guide

How to check, on your own PC, that `webhook-resiliency-service` turns real parcel events into signed webhooks, for
every parcel status the system can produce. Written 2026-11-27 against `dev` at `33c56175` (after PR #205).

There are two methods. Use the one that fits the question you are asking.

| | **Method A: synthetic events** | **Method B: real lifecycle** |
|---|---|---|
| What runs | RabbitMQ, `webhook-postgres`, the webhook service (and optionally the frontend in dev mode) | Gateway, auth, order, pricing, dispatch, delivery, merchant and webhook services, plus their databases, Redis and RabbitMQ |
| What you do | Publish hand-written `order.created` / `parcel.status.changed` messages to the local broker | Act as admin, merchant, dispatcher and courier through the real APIs |
| Time | Seconds per scenario | 30–60 min for the full set; only on a working day (see B.0) |
| Proves | Decoding, ownership, `NO_CHANGE`, dedupe, parking, fan-out, signing, retry, replay, Test Ping, the console | That the real producers emit the events the webhook service expects, for every transition |
| Does **not** prove | That any service would ever emit that message, or accept that transition | Edge cases that need precise control: overtaking, malformed messages, concurrency |
| Use it for | **Everyday webhook feature work**, and the regression checks after every change | **Before a release**, and after any upstream change to order, dispatch or delivery events |

Both are needed: A is fast and precise about the webhook service, B is the only proof of the integration. Automated
tests (section 6) cover much of A without manual work; run them for every change.

> **Never** publish synthetic events to the hosted (AKS) RabbitMQ, and never use production accounts here. Every
> command in this guide targets `localhost`.

Evidence labels used below: **[source]** read in the current code; **[test]** asserted by an automated test;
**[run 2026-11-27]** executed locally while writing this guide; **[not run]** derived from the code but not executed.

---

## 1. The parcel lifecycle as the code implements it

> The README's "Parcel lifecycle" section is **out of date**. It shows 9 states and says `RETURNED_TO_SENDER` has no
> delivery state. The code has 16 delivery states (hubs, failed attempts and returns were added) and 11 public
> statuses, and `RETURNED_TO_SENDER` is reached through the real `RETURNED` delivery state **[source]**.

### 1.1 Public statuses (what merchants receive)

`PublicOrderStatus` (common-contracts) has **11** values. Webhooks carry these, never the internal state.

| Delivery state (`DeliveryState`) | Public status | Internal sub-status |
|---|---|---|
| `CREATED` | `CREATED` | `CREATED` |
| `ASSIGNED` | `PICKUP_ASSIGNED` | `PICKUP_ASSIGNED` |
| `PICKED_UP` | `PICKED_UP` | `PICKED_UP` |
| `AT_ORIGIN_HUB` | `IN_TRANSIT` | `AT_HUB` |
| `IN_TRANSIT` | `IN_TRANSIT` | `OUTBOUND` |
| `AT_DESTINATION_HUB` | `IN_TRANSIT` | `INCOMING` |
| `OUT_FOR_DELIVERY` | `OUT_FOR_DELIVERY` | `OUT_FOR_DELIVERY` |
| `DELIVERED` | `DELIVERED` | `DELIVERED` |
| `ATTEMPT_FAILED` | `DELIVERY_ATTEMPTED` | `RETURN_TO_HUB` |
| `HELD_AT_HUB` | `DELIVERY_ATTEMPTED` | `HELD_AT_HUB` |
| `RETURN_IN_TRANSIT` | `RETURNING` | `RETURN_OUTBOUND` |
| `RETURN_AT_HUB` | `RETURNING` | `RETURN_AT_HUB` |
| `OUT_FOR_RETURN` | `RETURNING` | `OUT_FOR_RETURN` |
| `RETURNED` | `RETURNED_TO_SENDER` | `FAILED_RETURN` |
| `FAILED` | `FAILED` | `FAILED_ATTEMPT` |
| `CANCELLED` | `CANCELLED` | `CANCELLED` |

Source: `PublicOrderStatus.fromDeliveryState`, `InternalQueueStatus.fromDeliveryState` **[source]**.

### 1.2 Who emits `parcel.status.changed`

All three use routing key `parcel.status.changed` on `swiftdrop.events.exchange` and the `ParcelStatusChangedEvent`
record **[source]**:

| Producer | When | `previousPublicStatus` → `newPublicStatus` | After commit? |
|---|---|---|---|
| customer-order `OrderEventPublisher.publishOrderCreated` | Order booked (customer or merchant) | `null` → `CREATED` (sent right after `order.created`) | **No** (inside the order transaction) |
| customer-order `OrderEventPublisher.publishOrderCancelled` | `POST /api/v1/orders/{id}/cancel`, or fee revision rejected | **`CANCELLED` → `CANCELLED`** (see 1.4, issue 1) | No |
| dispatch `ManualOverrideService.publishAssignmentEvents` | First-mile courier assigned | `CREATED` → `PICKUP_ASSIGNED` | Yes (`AfterCommitRabbitRelay`) |
| delivery-execution `PublishStateTransitionAction` | **Every** delivery transition, including the `CREATED → ASSIGNED` it makes when it receives `dispatch.courier.assigned` | mapped from the previous and new delivery states (table 1.1) | Yes (`afterCommit`) |

Not every step emits one: a hub courier assignment (`AT_DESTINATION_HUB`/`RETURN_AT_HUB` + `dispatch.courier.assigned`)
and delivery's own handling of `order.cancelled` publish **no** status event **[source]**.

### 1.3 Transitions, how to trigger them, and what the merchant should get

The delivery service enforces the graph (`statemachine/states/*`, `HubRoute`, `ReturnRoute`) **[source]**.
"Webhook" means a `parcel.status.changed` delivery to every active endpoint of the owning merchant that subscribes to
it. `NO_CHANGE` means the webhook service drops the event because previous and new public status are equal (PR #203).

Merchant (B2B) orders are always `STANDARD` or `EXPRESS`, so they always go **through the hub**: `PICKED_UP` → hand-in
→ `AT_ORIGIN_HUB` **[source: `HubRoute.goesViaHub`, `B2bOrderRequest.speed`]**. The direct
`PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY` route exists only for `SAME_DAY` customer bookings.

| # | Transition (delivery state) | Public | Triggered by (gateway path; role) | Prerequisites | Webhook |
|---|---|---|---|---|---|
| 1 | booked → `CREATED` | `null → CREATED` | B2B: `POST /api/v1/b2b/orders` with `X-API-Key` (merchant API key) | Approved merchant, API key | ✅ `order.created`, then ✅ `CREATED` |
| 2 | `CREATED → ASSIGNED` | `CREATED → PICKUP_ASSIGNED` | `POST /api/v1/dispatch/shops/{merchantId}/assign` (shop visit) or `POST /api/v1/dispatch/assign`; DISPATCHER of the pickup district or SYSTEM_ADMIN | Standard: merchant marked parcels ready (`POST /api/v1/b2b/pickups/ready`). Courier on shift with a vehicle that fits. Before 15:00 Colombo time | ✅ **twice** (dispatch and delivery both publish; issue 2) |
| 3 | `ASSIGNED → PICKED_UP` | `PICKUP_ASSIGNED → PICKED_UP` | Shop: `POST /api/v1/deliveries/shops/{merchantId}/collect` `{deliveryIds, cashCollected}`. Single: `POST /api/v1/deliveries/{id}/transition` `{targetState:"PICKED_UP", cashCollected}`; COURIER who owns it | `cashCollected` must equal the fee(s) exactly, else 409 | ✅ |
| 4 | `PICKED_UP → AT_ORIGIN_HUB` | `PICKED_UP → IN_TRANSIT` | `POST /api/v1/deliveries/hand-in` `{deliveryIds}`; COURIER | — | ✅ |
| 5a | `AT_ORIGIN_HUB → AT_DESTINATION_HUB` (same district: "sorted") | `IN_TRANSIT → IN_TRANSIT` | `POST /api/v1/deliveries/{id}/transition` `{targetState:"AT_DESTINATION_HUB"}`; DISPATCHER of that district or SYSTEM_ADMIN | — | ❌ `NO_CHANGE` |
| 5b | `AT_ORIGIN_HUB → IN_TRANSIT` (other district: "Outbound") | `IN_TRANSIT → IN_TRANSIT` | transition `IN_TRANSIT`; DISPATCHER of the pickup district | Districts differ | ❌ `NO_CHANGE` |
| 5c | `IN_TRANSIT → AT_DESTINATION_HUB` ("accepted") | `IN_TRANSIT → IN_TRANSIT` | transition `AT_DESTINATION_HUB`; DISPATCHER of the drop-off district | — | ❌ `NO_CHANGE` |
| 6 | hub courier assigned | (no status change) | `POST /api/v1/dispatch/assign` `{orderId, courierId}`; DISPATCHER/SYSTEM_ADMIN | Courier on shift in that district | ❌ none emitted |
| 7 | `AT_DESTINATION_HUB → OUT_FOR_DELIVERY` | `IN_TRANSIT → OUT_FOR_DELIVERY` | `POST /api/v1/deliveries/load` `{deliveryIds}`; the assigned COURIER | Step 6 | ✅ |
| 8 | `OUT_FOR_DELIVERY → DELIVERED` | `OUT_FOR_DELIVERY → DELIVERED` | 1. `POST /api/v1/deliveries/{id}/proof-of-delivery` (multipart `photo` JPEG/PNG ≤ 5 MB, `recipientName`, optional `signature`). 2. transition `DELIVERED`; COURIER | Proof uploaded **after** it went out (`proofSince`) | ✅ |
| 9 | `OUT_FOR_DELIVERY → ATTEMPT_FAILED` | `OUT_FOR_DELIVERY → DELIVERY_ATTEMPTED` | transition `{targetState:"ATTEMPT_FAILED", failureReason}` (a `FAILED` request without a loss reason resolves to this too); COURIER | `failureReason` required | ✅ with `failureReason` |
| 10 | `ATTEMPT_FAILED → HELD_AT_HUB` | `DELIVERY_ATTEMPTED → DELIVERY_ATTEMPTED` | `POST /api/v1/deliveries/hand-in`; COURIER | — | ❌ `NO_CHANGE` |
| 11 | `HELD_AT_HUB → AT_DESTINATION_HUB` (try again) | `DELIVERY_ATTEMPTED → IN_TRANSIT` | transition; DISPATCHER of the drop-off district | Fewer than 2 attempts and not refused | ✅ |
| 12 | `HELD_AT_HUB → RETURN_AT_HUB` (same district) / `→ RETURN_IN_TRANSIT` (other) | `DELIVERY_ATTEMPTED → RETURNING` | transition; DISPATCHER of the hub holding it | — | ✅ |
| 13 | `RETURN_IN_TRANSIT → RETURN_AT_HUB` | `RETURNING → RETURNING` | transition; DISPATCHER of the **pickup** district | — | ❌ `NO_CHANGE` |
| 14 | `RETURN_AT_HUB → OUT_FOR_RETURN` | `RETURNING → RETURNING` | assign courier (step 6), then `POST /api/v1/deliveries/load` | — | ❌ `NO_CHANGE` |
| 15 | `OUT_FOR_RETURN → RETURNED` | `RETURNING → RETURNED_TO_SENDER` | proof photo upload, then transition `RETURNED`; COURIER | Photo taken after it went out | ✅ |
| 16 | any active state → `FAILED` | `… → FAILED` | transition `{targetState:"FAILED", failureReason:"LOST"\|"DAMAGED"\|"DAMAGED_IN_TRANSIT"}`; courier, dispatcher or admin | A **loss** reason (from `ASSIGNED`, any reason) | ✅ |
| 17 | `CREATED/ASSIGNED → CANCELLED` via the **delivery** API | `PICKUP_ASSIGNED → CANCELLED` | transition `CANCELLED` | Only from `ASSIGNED` in practice (a `CREATED` session exists only after assignment) | ✅ |
| 18 | order cancelled via the **order** API | `CANCELLED → CANCELLED` | `POST /api/v1/orders/{orderId}/cancel` `{reason}`; DISPATCHER/SYSTEM_ADMIN at any non-final status, the booking customer only while `CREATED` | — | ❌ **dropped (issue 1)** |
| 19 | `OUT_FOR_DELIVERY → AT_DESTINATION_HUB` (not reached today) | `OUT_FOR_DELIVERY → IN_TRANSIT` | `POST /api/v1/deliveries/hand-in`; COURIER | — | ✅ (status goes "backwards") |

Every public status is reachable locally through Method B. `RETURNED_TO_SENDER` needs the longest chain (rows 7, 9,
10, 12, 14, 15). `FAILED` and `CANCELLED` (row 17) are one call each.

### 1.4 Known issues found while writing this guide

1. **Order-service cancellations never reach merchants.** `publishOrderCancelled` sets `previousPublicStatus` from
   `order.getStatus()` *after* the order was set to `CANCELLED`, so the event is `CANCELLED → CANCELLED`. The PR #203
   `NO_CHANGE` rule drops it **[source; the webhook side was reproduced, run 2026-11-27]**. Delivery's own
   `order.cancelled` handler publishes nothing. Owner: Member 3 (order service). It also affects every other consumer
   that filters on "status changed" (notification-service `EventEnvelope` has the same check).
2. **`PICKUP_ASSIGNED` is sent twice** for a first-mile assignment: dispatch publishes `CREATED → PICKUP_ASSIGNED`, and
   delivery publishes the same change when it consumes `dispatch.courier.assigned`. Different event ids, so both
   become webhooks **[source; not yet observed at runtime]**. Owners: Members 5 and 6.
3. **Statuses can go backwards** (row 19, and row 11 after a failed attempt). That is real, not a webhook bug.
   Merchants must order by `occurredAt`.

---

## 2. Common setup

### 2.1 Ports, containers and credentials (verified 2026-11-27)

| What | Where | Credentials |
|---|---|---|
| RabbitMQ AMQP / management UI | `localhost:5672` / http://localhost:15672 | `swiftdrop` / `changeme_rabbit` (`.env` defaults and `infra/local/rabbitmq/definitions.json`) |
| Webhook database | `localhost:5438`, db `swiftdrop_webhook` (container `swiftdrop-webhook-postgres`) | `swiftdrop` / `changeme_pg` |
| Webhook service | `127.0.0.1:8087` only (loopback; it trusts identity headers) | none (identity headers) |
| Gateway (Method B) | http://localhost:8080 | JWT from `/api/v1/auth/login` |
| Frontend | http://localhost:5173 | — |

Queue `webhook.events.queue` (quorum, TTL 24 h, DLX `swiftdrop.dlx.exchange` → `dlq.webhook.events.queue`) is bound to
`order.created`, `parcel.status.changed.#` and `webhook.dispatch.requested` **[source: `RabbitMQConfig`,
`definitions.json`]**. A local broker created by older code may also show a stale `webhook.#` binding; it is harmless
(anything it routes is rejected to the DLQ) and is not in the repository.

Migrations V1–V4 apply automatically on startup (Flyway). V4 (`one_active_replay`) came with PR #179; no manual step
and no clean database is needed. **Never run `docker compose down -v`.**

### 2.2 Build and start (Method A set)

```powershell
# repo root
docker compose up -d rabbitmq webhook-postgres
docker compose --profile apps up -d --build webhook-resiliency-service   # rebuild after code changes
docker compose ps
Invoke-RestMethod http://localhost:8087/actuator/health                    # {"status":"UP"...}
docker logs -f swiftdrop-webhook-resiliency-service
```

Compile only (the host has no JDK 25; CI uses JDK 25):

```powershell
docker run --rm -v "${PWD}\backend:/workspace" -v "$env:USERPROFILE\.m2:/root/.m2" -w /workspace `
  maven:3-eclipse-temurin-25 mvn -B -q --no-transfer-progress -pl webhook-resiliency-service -am -DskipTests compile
```

### 2.3 A receiver for the webhooks

Pick one:

- **webhook.site** (public HTTPS, nothing to configure). Shows the headers and the raw body. Use a fresh URL per session.
- **Local mock** (lets you choose the reply code, and verifies signatures). The service runs in Docker, so it reaches the
  host as `host.docker.internal`, which the SSRF policy refuses unless the exact `host:port` is on the development
  allowlist. In the gitignored `docker-compose.override.yml`, set
  `WEBHOOK_SECURITY_DEV_HTTP_ALLOWLIST: host.docker.internal:9090`, run
  `docker compose --profile apps up -d --no-deps webhook-resiliency-service`, and check
  `docker logs swiftdrop-webhook-resiliency-service 2>&1 | Select-String "DEVELOPMENT ONLY"`. Put the empty value back
  afterwards. The service refuses to start with a non-empty allowlist under the `aks` profile.

Mock receiver (save outside the repo, e.g. `$env:TEMP\wh_mock.py`; run `python $env:TEMP\wh_mock.py`). Set
`$env:WH_SECRET` to the endpoint's signing secret before starting it to check every signature:

```python
import hashlib, hmac, http.server, json, os, time
SECRET = os.environ.get('WH_SECRET', '')
MODE = {'code': 200}
class H(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path.startswith('/__mode/'):          # POST /__mode/503 changes the reply code
            MODE['code'] = int(self.path.rsplit('/', 1)[1]); self.send_response(204); self.end_headers(); return
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        ts, sig = self.headers.get('X-SwiftDrop-Timestamp', ''), self.headers.get('X-SwiftDrop-Signature', '')
        ok = None
        if SECRET and ts.isdigit():
            expected = 'sha256=' + hmac.new(SECRET.encode(), ts.encode() + b'.' + body, hashlib.sha256).hexdigest()
            ok = hmac.compare_digest(expected, sig) and abs(time.time() - int(ts)) <= 300
        doc = json.loads(body or b'{}')
        print(json.dumps({'event': self.headers.get('X-SwiftDrop-Event'), 'eventId': self.headers.get('X-SwiftDrop-Event-Id'),
                          'deliveryId': self.headers.get('X-SwiftDrop-Delivery-Id'), 'attempt': self.headers.get('X-SwiftDrop-Attempt'),
                          'signatureValid': ok, 'status': doc.get('data', {}).get('status'),
                          'previous': doc.get('data', {}).get('previousStatus'), 'reply': MODE['code']}), flush=True)
        self.send_response(MODE['code']); self.end_headers()
    def log_message(self, *args): pass
http.server.ThreadingHTTPServer(('0.0.0.0', 9090), H).serve_forever()
```

Change the reply code from PowerShell: `Invoke-RestMethod -Method Post http://localhost:9090/__mode/503`.

### 2.4 Inspecting state

```powershell
docker exec -it swiftdrop-webhook-postgres psql -U swiftdrop -d swiftdrop_webhook
```

```sql
-- what was ingested for an order, and the outcome
SELECT event_type, processing_status, merchant_id, payload->>'previousPublicStatus' prev,
       payload->>'newPublicStatus' new, occurred_at FROM inbound_events WHERE order_id = '<orderId>' ORDER BY occurred_at;
-- its delivery jobs per endpoint
SELECT d.delivery_id, e.description, i.payload->>'newPublicStatus' status, d.status, d.attempt_count,
       d.next_attempt_at, d.replay_of_delivery_id
FROM webhook_deliveries d JOIN webhook_endpoints e USING (endpoint_id) JOIN inbound_events i ON i.event_id = d.inbound_event_id
WHERE i.order_id = '<orderId>' ORDER BY i.occurred_at, d.created_at;
-- attempts of one delivery
SELECT attempt_number, http_status_code, error_class, latency_ms, attempted_at
FROM webhook_delivery_attempts WHERE delivery_id = '<deliveryId>' ORDER BY attempt_number;
```

`NO_CHANGE` events leave **no row** (they are dropped before the inbox insert). Look for them in the log instead:

```powershell
docker logs --since 10m swiftdrop-webhook-resiliency-service 2>&1 | Select-String "outcome="
# outcomes: RESOLVED, NO_MERCHANT, AWAITING_MERCHANT, DUPLICATE, NO_CHANGE
docker logs --since 10m swiftdrop-webhook-resiliency-service 2>&1 | Select-String "Webhook delivery|recorded"
```

Broker DLQ depth (rejected inbound messages):

```powershell
$RabbitAuth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('swiftdrop:changeme_rabbit')) }
(Invoke-RestMethod http://localhost:15672/api/queues/%2F/dlq.webhook.events.queue -Headers $RabbitAuth).messages
```

### 2.5 The webhook API without the frontend

The service trusts gateway-style identity headers on its loopback port. Use a merchant id **per test session**, so you
never fan out to endpoints you registered for something else (the dev merchant `…a1` has real webhook.site endpoints).

```powershell
$Wh = 'http://localhost:8087/api/v1/webhooks'
$TestMerchant = [guid]::NewGuid().ToString()          # or a real merchant id (Method B)
$AsMerchant = @{ 'X-User-Id' = [guid]::NewGuid().ToString(); 'X-User-Role' = 'MERCHANT'; 'X-Merchant-Id' = $TestMerchant }

# register (the signing secret is returned ONCE)
$created = Invoke-RestMethod -Method Post "$Wh/subscriptions" -Headers $AsMerchant -ContentType 'application/json' `
  -Body (@{ targetUrl = 'https://webhook.site/<your-id>'; subscribedEvents = 'order.created,parcel.status.changed'; description = 'local test' } | ConvertTo-Json)
$EndpointId = $created.data.endpoint.endpointId; $Secret = $created.data.signingSecret

Invoke-RestMethod "$Wh/subscriptions" -Headers $AsMerchant                                   # list (no secrets)
Invoke-RestMethod -Method Post "$Wh/subscriptions/$EndpointId/test" -Headers $AsMerchant     # Test Ping
Invoke-RestMethod "$Wh/subscriptions/$EndpointId/attempts" -Headers $AsMerchant              # attempts (pings + deliveries)
Invoke-RestMethod -Method Patch "$Wh/subscriptions/$EndpointId/status?active=false" -Headers $AsMerchant   # pause
Invoke-RestMethod "$Wh/deliveries/failed" -Headers $AsMerchant                               # DEAD deliveries
Invoke-RestMethod -Method Post "$Wh/deliveries/failed/<deliveryId>/replay" -Headers $AsMerchant   # 202
Invoke-RestMethod -Method Post "$Wh/deliveries/failed/replay" -Headers $AsMerchant           # replay all, returns a count
```

For the local mock use `targetUrl = 'http://host.docker.internal:9090/hook'` (allowlist on, 2.3).

### 2.6 The console (frontend dev mode)

`npm run dev:webhook` (in `frontend/`) still works as before **[source]**. It starts a fake MERCHANT browser session, and
the Vite dev server sends `/api/v1/webhooks/**` straight to `localhost:8087` with the fixed identity
`X-User-Role: MERCHANT`, `X-Merchant-Id: 00000000-0000-4000-8000-0000000000a1`. It matches the console route
`/merchant/webhooks`, which is MERCHANT-only since PR #179. Open http://localhost:5173/merchant/webhooks: endpoints,
Test Ping, the attempts dialog, event checkboxes and the **Failed events** table with Replay / Replay all.
`/merchant/api-guide` is a static page and works too. To view another merchant (a test merchant, or a real one from
Method B), create `frontend/.env.webhook-dev.local` (gitignored) with `WEBHOOK_DEV_MERCHANT_ID=<uuid>` and restart.

There is no console screen for the legacy `/api/v1/webhooks/dlq` API any more, and no admin route to the console.
Since Step 17 that API is read-only history: `GET /dlq` and `GET /dlq/{id}` for MERCHANT (own records) and
SYSTEM_ADMIN (403 for every other role, including DISPATCHER), and `POST /dlq/{id}/replay` and `POST /dlq/replay-all`
always answer **410 Gone** without sending anything. Check it with:

```powershell
Invoke-RestMethod "$Wh/dlq" -Headers $AsMerchant                                    # history (usually empty)
try { Invoke-RestMethod -Method Post "$Wh/dlq/replay-all" -Headers $AsMerchant } catch { $_.Exception.Response.StatusCode.value__ }   # 410
```

---

## 3. Method A: synthetic events

### 3.1 Helpers (paste once per PowerShell 5.1 session)

These follow the current contracts (`OrderCreatedEvent`, `ParcelStatusChangedEvent`) and the decoder: `eventId`,
`orderId`, `correlationId` (canonical UUIDs) and `timestamp` are required, `newPublicStatus` must be a valid public
status, and enum values must be known. `codAmount` no longer exists (PR #183) and is not sent. Each message is
tagged `app_id = manual-test`, which is stored as `inbound_events.source_service`, so synthetic rows are easy to find.

```powershell
$RabbitPublish = 'http://localhost:15672/api/exchanges/%2F/swiftdrop.events.exchange/publish'
$RabbitAuth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('swiftdrop:changeme_rabbit')) }
function Get-UtcNow { (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'") }

function Publish-SwiftDropEvent([string]$RoutingKey, [hashtable]$Event) {
  $message = @{ properties = @{ content_type = 'application/json'; app_id = 'manual-test' }; routing_key = $RoutingKey
                payload = ($Event | ConvertTo-Json -Compress); payload_encoding = 'string' } | ConvertTo-Json -Compress
  $r = Invoke-RestMethod -Method Post -Uri $RabbitPublish -Headers $RabbitAuth -ContentType 'application/json' -Body $message
  if (-not $r.routed) { throw "Not routed: $RoutingKey" }
  $Event.eventId
}

# A shop's order names the shop as merchant AND as customer (the order service stores the merchant id as customerId).
function New-OrderCreated([string]$OrderId, [string]$Tracking, [string]$MerchantId, [string]$CustomerId, [string]$Speed = 'STANDARD') {
  $e = @{ eventId = [guid]::NewGuid().ToString(); correlationId = [guid]::NewGuid().ToString(); orderId = $OrderId
          trackingNumber = $Tracking; pickupDistrict = 'COLOMBO'; deliveryDistrict = 'COLOMBO'; speed = $Speed
          currency = 'LKR'; timestamp = (Get-UtcNow) }
  if ($MerchantId) { $e.merchantId = $MerchantId }
  if ($CustomerId) { $e.customerId = $CustomerId }
  $e
}

function New-StatusChanged([string]$OrderId, [string]$Tracking, $Previous, [string]$New, [string]$Internal, [string]$CustomerId, $FailureReason = $null) {
  @{ eventId = [guid]::NewGuid().ToString(); correlationId = [guid]::NewGuid().ToString(); orderId = $OrderId
     trackingNumber = $Tracking; customerId = $CustomerId; previousPublicStatus = $Previous; newPublicStatus = $New
     internalSubStatus = $Internal; district = 'COLOMBO'; destinationDistrict = 'COLOMBO'; failureReason = $FailureReason
     timestamp = (Get-UtcNow) }
}
```

Use the merchant id you registered the endpoint for in 2.5 (`$TestMerchant`), and one order per scenario:

```powershell
$Order = [guid]::NewGuid().ToString(); $Tracking = 'SD-TEST-' + (Get-Random -Minimum 1000 -Maximum 9999)
Publish-SwiftDropEvent 'order.created' (New-OrderCreated $Order $Tracking $TestMerchant $TestMerchant)
```

The listener runs 2–4 consumers, so two messages published back to back may be processed in either order. Wait a
second between messages whose order matters.

### 3.2 Every public status, as the real producers send it

This replays the sequence a real merchant order produces (section 1.3), including the steps that must **not** become
webhooks. Expected results are the code's behaviour; the ingestion outcomes marked **[run 2026-11-27]** were executed
locally with a merchant that has no endpoints.

```powershell
function Step($prev, $new, $internal, $reason = $null) {
  Publish-SwiftDropEvent 'parcel.status.changed' (New-StatusChanged $Order $Tracking $prev $new $internal $TestMerchant $reason) | Out-Null
  Start-Sleep -Milliseconds 800
}
Step $null              'CREATED'            'CREATED'             # webhook                  [run: RESOLVED]
Step 'CREATED'          'PICKUP_ASSIGNED'    'PICKUP_ASSIGNED'     # webhook                  [run: RESOLVED]
Step 'PICKUP_ASSIGNED'  'PICKED_UP'          'PICKED_UP'           # webhook
Step 'PICKED_UP'        'IN_TRANSIT'         'AT_HUB'              # webhook (handed in)
Step 'IN_TRANSIT'       'IN_TRANSIT'         'INCOMING'            # NO webhook: NO_CHANGE    [run: NO_CHANGE]
Step 'IN_TRANSIT'       'OUT_FOR_DELIVERY'   'OUT_FOR_DELIVERY'    # webhook
Step 'OUT_FOR_DELIVERY' 'DELIVERY_ATTEMPTED' 'RETURN_TO_HUB' 'REFUSED_BY_CUSTOMER'   # webhook, failureReason set
Step 'DELIVERY_ATTEMPTED' 'DELIVERY_ATTEMPTED' 'HELD_AT_HUB'       # NO webhook: NO_CHANGE
Step 'DELIVERY_ATTEMPTED' 'RETURNING'        'RETURN_AT_HUB'       # webhook
Step 'RETURNING'        'RETURNING'          'OUT_FOR_RETURN'      # NO webhook: NO_CHANGE
Step 'RETURNING'        'RETURNED_TO_SENDER' 'FAILED_RETURN'       # webhook
```

Separate orders for the other final statuses (create each order first, as above):

```powershell
Step 'PICKUP_ASSIGNED'  'DELIVERED'  'DELIVERED'                    # (shape only; the real path is OUT_FOR_DELIVERY → DELIVERED)
Step 'PICKED_UP'        'FAILED'     'FAILED_ATTEMPT' 'LOST'        # webhook, failureReason LOST
Step 'PICKUP_ASSIGNED'  'CANCELLED'  'CANCELLED'                    # webhook (delivery-service cancel)
Step 'CANCELLED'        'CANCELLED'  'CANCELLED'                    # what the ORDER service sends: NO_CHANGE, no webhook  [run: NO_CHANGE]
```

Expected for a subscribed, active endpoint: exactly one request per "webhook" line, in order, each with
`X-SwiftDrop-Event: parcel.status.changed`, `X-SwiftDrop-Event-Id` = the published `eventId`, `X-SwiftDrop-Attempt: 1`,
and a body like:

```json
{"id":"<eventId>","type":"parcel.status.changed","apiVersion":1,"occurredAt":"<timestamp>",
 "data":{"orderId":"<orderId>","merchantOrderReference":null,"trackingNumber":"<from order.created>",
         "status":"OUT_FOR_DELIVERY","previousStatus":"IN_TRANSIT","failureReason":null}}
```

No `customerId`, districts, internal sub-status, correlation id or money fields ever appear. The tracking number
comes from the order's `order.created`, not from the status event.

### 3.3 Ownership (PR #203) and tenant isolation

| Publish `order.created` with | Expected outcome |
|---|---|
| `merchantId = M`, `customerId = M` (shop order, current producers) | `RESOLVED`, merchant M **[run 2026-11-27]** |
| `merchantId = M`, `customerId = someone else` (a customer booking naming a shop) | `NO_MERCHANT`; later status events of this order go nowhere **[run 2026-11-27]** |
| `merchantId = M`, no `customerId` (events from before #203) | `RESOLVED`, merchant M (legacy rule) **[run 2026-11-27]** |
| no `merchantId` | `NO_MERCHANT` |
| a second `order.created` for the same order naming another merchant | rejected to `dlq.webhook.events.queue`; first owner kept **[test]** |

Isolation check: register endpoints for two test merchants, publish events for M1's order, and confirm M2's receiver
gets nothing and `GET /deliveries/failed` as M2 never lists M1's rows.

### 3.4 Duplicates, out-of-order, rejected messages

```powershell
# duplicate: the same eventId twice → one inbox row, one webhook; log shows outcome=DUPLICATE   [run 2026-11-27]
$e = New-StatusChanged $Order $Tracking 'IN_TRANSIT' 'OUT_FOR_DELIVERY' 'OUT_FOR_DELIVERY' $TestMerchant
Publish-SwiftDropEvent 'parcel.status.changed' $e; Publish-SwiftDropEvent 'parcel.status.changed' $e

# status before its order: AWAITING_MERCHANT, then released (and delivered) when order.created arrives   [run 2026-11-27]
$Early = [guid]::NewGuid().ToString()
Publish-SwiftDropEvent 'parcel.status.changed' (New-StatusChanged $Early 'SD-EARLY-0001' 'CREATED' 'PICKUP_ASSIGNED' 'PICKUP_ASSIGNED' $TestMerchant)
Start-Sleep 2
Publish-SwiftDropEvent 'order.created' (New-OrderCreated $Early 'SD-EARLY-0001' $TestMerchant $TestMerchant)

# rejected: lands in dlq.webhook.events.queue, nothing stored, the log names the field (never the value)
Publish-SwiftDropEvent 'parcel.status.changed' @{ eventId = [guid]::NewGuid().ToString(); orderId = 'not-a-uuid' }
```

Peek at the DLQ without removing messages: management UI → Queues → `dlq.webhook.events.queue` → Get messages, with
Ack mode "Nack message requeue true".

**Ordering.** Delivery is FIFO per (endpoint, order) by the event's `occurredAt`: a newer event waits while an older
one is `PENDING`/`RETRY`/`IN_FLIGHT`. A `DEAD` or `SUCCEEDED` older event does not block. An event published late with
an older `timestamp` is sent when it arrives; nothing is reordered after the fact.

### 3.5 Signature, retry, replay and Test Ping (local mock)

With the mock (2.3) running with `$env:WH_SECRET` set to the endpoint's secret:

| Scenario | Steps | Expected |
|---|---|---|
| Signature | publish any status event | mock prints `signatureValid: true`; `X-SwiftDrop-Timestamp` in Unix **seconds** |
| Retry | `POST /__mode/503`, publish an event; after the 2nd attempt `POST /__mode/200` | attempts 1, 2 (≈30 s ±20 %), 3 (≈2 min) …: same `X-SwiftDrop-Delivery-Id` and `Event-Id`, `Attempt` 1, 2, 3; job `SUCCEEDED` |
| Exhausted | keep 503 | 4 attempts (0, ≈30 s, ≈2 min, ≈10 min), then `DEAD`; listed by `GET /deliveries/failed` with `lastHttpStatus` 503 |
| Permanent | `POST /__mode/400` (or 302) | 1 attempt, `DEAD` at once; a redirect is never followed |
| Replay | with a `DEAD` delivery: `POST /__mode/200`, then `POST /deliveries/failed/{id}/replay` | 202; a new job with `replay_of_delivery_id` = original; request with the **same** Event-Id, a **new** Delivery-Id, Attempt 1 |
| One active replay | call replay twice quickly | still one replay row (index `uq_webhook_deliveries_active_replay`) |
| Replay after success | replay the same original again | refused (nothing queued); a replay that ended `DEAD` can be replayed again |
| Paused endpoint | pause, publish, wait, activate | no request while paused (job stays `PENDING`), sent after activation |
| Test Ping | `POST /subscriptions/{id}/test` | one request, type `webhook.test.ping`, same headers and signature scheme; no `webhook_deliveries` row; one attempt row; never retried |
| Unsafe URL | register `https://127.0.0.1/hook` or `http://example.com/hook` | 400 "Invalid Webhook URL" |

A quicker retry schedule is not configurable without recreating the container with an overridden
`webhook.delivery.retry-delays` (Spring env form `WEBHOOK_DELIVERY_RETRYDELAYS=3s,5s,8s`) **[not run]**. The automated
`WebhookDeliveryFlowIT` already runs retry and replay with 200 ms delays.

**Replay ordering caveat.** A replay carries its original's `occurredAt`. Newer events of the same order have usually
been delivered already, so the replayed (older) status arrives **after** them. Since R1 the merchant API guide says so
and tells merchants to compare `occurredAt` and keep the latest status.

---

## 4. Method B: the real lifecycle

> **Status of this section: [not run].** Every path, body and role below was taken from the current controllers,
> DTOs and the seeder (`scripts/seed/seed.mjs`, which drives the same calls), but this exact PowerShell sequence has
> not been executed yet. If a call is refused, the error message names the rule; please correct this section when you
> run it.

### B.0 Constraints to know first

- **Working hours** (dispatch `WorkingDay`, Asia/Colombo, uses the real clock): a courier shift starts only Monday–Saturday,
  07:30–17:00, not on a closed day; first-mile pickups are assigned only until **15:00**. Do steps B.5–B.6 in that window.
- **Memory**: about 8 Java services. Give Docker 6 GB or more (README).
- **No email**: accounts are created by the admin (verified from the start). Keep `AUTH_MFA_ENABLED=false`
  (the default) and `NOTIFICATION_EMAIL_PROVIDER=mock`.
- The demo seeder (`scripts/seed/seed.mjs`, see `scripts/seed/README.md`) performs this same flow for ~240 parcels;
  the calls below are the ones it uses. Running it (local Kubernetes) is an alternative to typing them.

### B.1 Start the stack

Set a bootstrap admin in your **local** `.env` (blank by default, so no admin exists):
`BOOTSTRAP_ADMIN_USERNAME=admin`, `BOOTSTRAP_ADMIN_PASSWORD=<a local password>`, `BOOTSTRAP_ADMIN_EMAIL=admin@example.com`.
It is created on auth-iam-service's first start with it set.

```powershell
# repo root. Infra first (all databases, RabbitMQ, Redis)
docker compose up -d
# the services this flow needs (notification and analytics are optional; their events wait in their queues)
docker compose --profile apps up -d --build auth-iam-service pricing-zone-service customer-order-service `
  dispatch-assignment-service delivery-execution-service merchant-b2b-service webhook-resiliency-service
# the gateway without its depends_on (it waits for every app service otherwise, including the optional two)
docker compose --profile apps up -d --build --no-deps api-gateway
docker compose ps        # wait until the services are (healthy); first start takes several minutes
```

Starting the whole stack (`docker compose --profile apps up -d --build`) also works if you have the memory.
Optional UI: `cd frontend; npm run dev` (normal mode, through the gateway; **not** `dev:webhook`).

### B.2 Helpers and accounts

```powershell
$Api = 'http://localhost:8080'
function Invoke-Sd([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null, [hashtable]$Extra = @{}) {
  $h = @{} + $Extra; if ($Token) { $h.Authorization = "Bearer $Token" }
  $req = @{ Method = $Method; Uri = "$Api$Path"; Headers = $h }
  if ($null -ne $Body) { $req.ContentType = 'application/json'; $req.Body = ($Body | ConvertTo-Json -Depth 6) }
  $r = Invoke-RestMethod @req
  if ($r -and $r.PSObject.Properties.Name -contains 'data') { $r.data } else { $r }
}
function Login($User, $Pass) { Invoke-Sd POST '/api/v1/auth/login' @{ username = $User; password = $Pass } }

$Admin = Login 'admin' '<bootstrap password>'
$Pw = 'Local-test-2026'
foreach ($u in @(
  @{ username='disp.colombo'; role='DISPATCHER'; firstName='Dina'; lastName='Perera';  phone='0771000001' },
  @{ username='courier.colombo'; role='COURIER'; firstName='Kamal'; lastName='Silva'; phone='0771000002' },
  @{ username='shop.owner';  role='MERCHANT';   firstName='Nimal'; lastName='Fernando'; phone='0771000003' })) {
  Invoke-Sd POST '/api/v1/users' ($u + @{ email = "$($u.username)@example.com"; password = $Pw; district = 'COLOMBO' }) $Admin.accessToken
}
$Disp = Login 'disp.colombo' $Pw; $Courier = Login 'courier.colombo' $Pw; $Owner = Login 'shop.owner' $Pw
```

### B.3 Approve the merchant, get an API key, register the webhook

```powershell
$App = Invoke-Sd POST '/api/v1/merchants/register' @{
  businessName = 'Local Test Shop'; businessCategory = 'ELECTRONICS'; monthlyParcelVolume = 'UP_TO_50'
  businessRegNo = 'PV00012345'; contactEmail = 'shop@example.com'; contactPhone = '0771000004'
  pickupAddress = 'No. 10, Galle Road'; pickupCity = 'Colombo'; pickupPostalCode = '00300'; operatingDistrict = 'COLOMBO' } $Owner.accessToken
$App = Invoke-Sd POST "/api/v1/admin/merchants/$($App.merchantId)/approve" $null $Admin.accessToken
$MerchantId = $App.merchantId

# IAM links the account from the merchant.approved event: sign in again until the token carries merchantId
function Get-JwtClaims($t) { $p = $t.Split('.')[1].Replace('-','+').Replace('_','/'); $p += '=' * ((4 - $p.Length % 4) % 4)
  [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json }
do { Start-Sleep 2; $Owner = Login 'shop.owner' $Pw } until ((Get-JwtClaims $Owner.accessToken).merchantId -eq $MerchantId)

$Key = Invoke-Sd POST "/api/v1/merchants/$MerchantId/api-keys" @{ keyName = 'Local test'; expiresInDays = 30 } $Owner.accessToken
$ApiKey = $Key.rawApiKey

# webhook endpoint, through the gateway as the real merchant (secret shown once)
$Ep = Invoke-Sd POST '/api/v1/webhooks/subscriptions' @{ targetUrl = 'https://webhook.site/<your-id>'
        subscribedEvents = 'order.created,parcel.status.changed'; description = 'method B' } $Owner.accessToken
$Ep.signingSecret
```

UI alternative: sign in as `shop.owner` at http://localhost:5173 (normal `npm run dev`), open **Webhooks**, Register.

### B.4 Courier vehicle and shift (working hours)

```powershell
$Vehicle = @{ vehicleType = 'MOTORCYCLE'; plateNumber = 'SDA-1001'; vehicleModel = 'Honda Dio'; vehicleColour = 'White'; licenceNumber = 'B1000001' }
Invoke-Sd PUT '/api/v1/dispatch/couriers/me/profile' $Vehicle $Courier.accessToken
Invoke-Sd PUT "/api/v1/dispatch/couriers/$($Courier.userId)/profile" ($Vehicle + @{ maxCapacity = 20 }) $Disp.accessToken
Invoke-Sd POST '/api/v1/dispatch/couriers/shifts/start' @{ courierId = $Courier.userId; courierName = 'Kamal Silva'; phone = '0771000002' } $Courier.accessToken
```

### B.5 A merchant order: `CREATED` → `PICKUP_ASSIGNED` → `PICKED_UP`

```powershell
function New-B2bOrder($Ref) {
  Invoke-Sd POST '/api/v1/b2b/orders' @{ externalRef = $Ref
    pickup   = @{ addressLine1 = 'No. 10, Galle Road'; city = 'Colombo'; district = 'COLOMBO'; postalCode = '00300'; contactName = 'Local Test Shop'; contactPhone = '0771000004' }
    delivery = @{ addressLine1 = 'No. 25, Duplication Road'; city = 'Colombo'; district = 'COLOMBO'; postalCode = '00300'; contactName = 'Test Recipient'; contactPhone = '0711234567' }
    parcel   = @{ actualWeightKg = 1.2; lengthCm = 25; widthCm = 20; heightCm = 10; prohibitedItemsDeclared = $true; itemDescription = 'Books' }
    speed    = 'STANDARD' } $null @{ 'X-API-Key' = $ApiKey }
}
$O = New-B2bOrder ('LT-' + (Get-Random -Minimum 10000 -Maximum 99999))   # → order.created + CREATED webhooks
$O.orderId; $O.trackingNumber

Invoke-Sd POST '/api/v1/b2b/pickups/ready' $null $null @{ 'X-API-Key' = $ApiKey }        # the shop has packed it
Start-Sleep 3
Invoke-Sd POST "/api/v1/dispatch/shops/$MerchantId/assign" @{ courierId = $Courier.userId } $Disp.accessToken   # → PICKUP_ASSIGNED (x2, issue 2)
Start-Sleep 3
$Stop = Invoke-Sd GET "/api/v1/deliveries/shops/$MerchantId" $null $Courier.accessToken
$Fees = ($Stop.parcels | Measure-Object -Property fee -Sum).Sum
Invoke-Sd POST "/api/v1/deliveries/shops/$MerchantId/collect" @{ deliveryIds = @($Stop.parcels.deliveryId); cashCollected = $Fees } $Courier.accessToken   # → PICKED_UP
$D = (Invoke-Sd GET "/api/v1/deliveries/order/$($O.orderId)" $null $Courier.accessToken).deliveryId
```

`/collect` needs `deliveryIds` to be an array even for one parcel (hence `@(...)`), and the exact fee total.

### B.6 Hub, out for delivery, delivered

```powershell
function Move($Id, $Target, $Token, $Extra = @{}) { Invoke-Sd POST "/api/v1/deliveries/$Id/transition" (@{ targetState = $Target } + $Extra) $Token }

Invoke-Sd POST '/api/v1/deliveries/hand-in' @{ deliveryIds = @($D) } $Courier.accessToken          # AT_ORIGIN_HUB → IN_TRANSIT webhook
Move $D 'AT_DESTINATION_HUB' $Disp.accessToken                                                     # same district: NO_CHANGE, no webhook
Start-Sleep 3
Invoke-Sd POST '/api/v1/dispatch/assign' @{ orderId = $O.orderId; courierId = $Courier.userId; assignedBy = 'disp.colombo'; reason = 'Next run' } $Disp.accessToken   # no status event
Start-Sleep 3
Invoke-Sd POST '/api/v1/deliveries/load' @{ deliveryIds = @($D) } $Courier.accessToken            # → OUT_FOR_DELIVERY webhook

# proof of delivery (PowerShell 5.1 has no -Form; use the built-in curl.exe). Any real JPEG/PNG under 5 MB.
curl.exe -s -X POST "$Api/api/v1/deliveries/$D/proof-of-delivery" -H "Authorization: Bearer $($Courier.accessToken)" `
  -F "photo=@C:\path\to\parcel.jpg;type=image/jpeg" -F "recipientName=Test Recipient" -F "signature=Signed by Test Recipient"
Move $D 'DELIVERED' $Courier.accessToken                                                           # → DELIVERED webhook
```

Different districts instead (`delivery.district` ≠ `COLOMBO`): after hand-in the **pickup** district's dispatcher
marks it Outbound (`Move $D 'IN_TRANSIT'`), and the **drop-off** district's dispatcher accepts it
(`Move $D 'AT_DESTINATION_HUB'`), assigns one of *their* couriers, who loads it. You need a dispatcher and a courier
on shift in both districts. Both hub steps are `NO_CHANGE`.

### B.7 The other endings (one new order each, repeat B.5 first)

```powershell
# DELIVERY_ATTEMPTED, then RETURNED_TO_SENDER (same district)
# ... B.6 up to OUT_FOR_DELIVERY, then:
Move $D 'ATTEMPT_FAILED' $Courier.accessToken @{ failureReason = 'REFUSED_BY_CUSTOMER'; notes = 'Refused at the door' }   # → DELIVERY_ATTEMPTED webhook
Invoke-Sd POST '/api/v1/deliveries/hand-in' @{ deliveryIds = @($D) } $Courier.accessToken       # HELD_AT_HUB: NO_CHANGE
Move $D 'RETURN_AT_HUB' $Disp.accessToken                                                       # → RETURNING webhook
Start-Sleep 3
Invoke-Sd POST '/api/v1/dispatch/assign' @{ orderId = $O.orderId; courierId = $Courier.userId; assignedBy = 'disp.colombo'; reason = 'Back to the sender' } $Disp.accessToken
Start-Sleep 3
Invoke-Sd POST '/api/v1/deliveries/load' @{ deliveryIds = @($D) } $Courier.accessToken         # OUT_FOR_RETURN: NO_CHANGE
curl.exe -s -X POST "$Api/api/v1/deliveries/$D/proof-of-delivery" -H "Authorization: Bearer $($Courier.accessToken)" `
  -F "photo=@C:\path\to\parcel.jpg;type=image/jpeg" -F "recipientName=Local Test Shop"
Move $D 'RETURNED' $Courier.accessToken                                                         # → RETURNED_TO_SENDER webhook

# Try again instead of returning: use a retryable reason (e.g. CUSTOMER_UNAVAILABLE) for ATTEMPT_FAILED, hand in, then
Move $D 'AT_DESTINATION_HUB' $Disp.accessToken                                                  # → IN_TRANSIT webhook (DELIVERY_ATTEMPTED → IN_TRANSIT)
# then assign + load + proof + DELIVERED as in B.6. A refused parcel, or one tried twice, cannot be retried (400).

# FAILED (lost or damaged), e.g. right after pickup
Move $D 'FAILED' $Courier.accessToken @{ failureReason = 'LOST'; notes = 'Lost in transit' }   # → FAILED webhook

# CANCELLED through the delivery service (while ASSIGNED, before pickup)
Move $D 'CANCELLED' $Disp.accessToken                                                          # → CANCELLED webhook

# CANCELLED through the order service (any order not yet final): issue 1, expect NO webhook
Invoke-Sd POST "/api/v1/orders/$($O.orderId)/cancel" @{ reason = 'Shop cancelled' } $Disp.accessToken
docker logs --since 2m swiftdrop-webhook-resiliency-service 2>&1 | Select-String "outcome=NO_CHANGE"
```

### B.8 What to check after each run

1. The receiver got one request per ✅ row of table 1.3 that you performed (plus a second `PICKUP_ASSIGNED`, issue 2),
   none for the `NO_CHANGE` rows, and all signatures verify against `$Ep.signingSecret`.
2. `trackingNumber` in every body equals `$O.trackingNumber`; `merchantOrderReference` is `null` (Step 18 is blocked).
3. SQL from 2.4 with `$O.orderId`: every webhook row is `SUCCEEDED`; inbox rows have `processing_status = RESOLVED`
   and `merchant_id = $MerchantId`.
4. The console (as `shop.owner`) → Webhooks → Attempts shows the deliveries; **Failed events** is empty.
5. A customer booking (`POST /api/v1/orders` as a CUSTOMER, even with `merchantId` in the body) produces **no**
   webhook for the shop: the order service drops the field and the webhook service records `NO_MERCHANT`.

---

## 5. Expected results by status (summary)

| Public status | Method A | Method B | Webhook expected |
|---|---|---|---|
| `CREATED` | 3.2 | B.5 | yes (after `order.created`) |
| `PICKUP_ASSIGNED` | 3.2 | B.5 | yes, **twice** in B (issue 2) |
| `PICKED_UP` | 3.2 | B.5 | yes |
| `IN_TRANSIT` | 3.2 | B.6, B.7 retry | yes on entry; hub-to-hub steps `NO_CHANGE` |
| `OUT_FOR_DELIVERY` | 3.2 | B.6 | yes |
| `DELIVERED` | 3.2 | B.6 | yes (needs proof in B) |
| `DELIVERY_ATTEMPTED` | 3.2 | B.7 | yes, with `failureReason`; hand-in `NO_CHANGE` |
| `RETURNING` | 3.2 | B.7 | yes on entry; later return steps `NO_CHANGE` |
| `RETURNED_TO_SENDER` | 3.2 | B.7 | yes (needs proof photo in B) |
| `FAILED` | 3.2 | B.7 | yes, with `failureReason` |
| `CANCELLED` | 3.2 | B.7 | delivery-service cancel: yes. Order-service cancel: **no** (issue 1) |

---

## 6. Automated tests

| Command (repo root) | Runs | Needs |
|---|---|---|
| `mvn -f backend/pom.xml -pl webhook-resiliency-service -am test` | unit tests | JDK 25 |
| `mvn -f backend/pom.xml -pl webhook-resiliency-service -am verify -Dspring.profiles.active=integration-test` | unit + integration tests (Testcontainers PostgreSQL 16 and RabbitMQ 3.13) + JaCoCo report and **check** (this module: line ≥ 85 %, branch ≥ 68 %) | JDK 25, Docker |

Without a host JDK 25, run `verify` in the Maven container with the Docker socket (used on 2026-11-27):

```powershell
docker run --rm -v "${PWD}:/repo" -v "$env:USERPROFILE\.m2:/root/.m2" -v //var/run/docker.sock:/var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /repo/backend maven:3-eclipse-temurin-25 `
  mvn -B --no-transfer-progress -pl webhook-resiliency-service -am verify "-Dspring.profiles.active=integration-test"
```

The coverage report is `backend/webhook-resiliency-service/target/site/jacoco/index.html`.

Baseline on 2026-11-27 (`33c56175`) **[run 2026-11-27]**: BUILD SUCCESS, 187 unit tests and 43 integration tests, 0
failures, line coverage 88.2 % and branch coverage 72.9 %. That is only 3.2 and 4.9 points above the gate, so new code
without tests fails `verify` quickly. A "Surefire is going to kill self fork JVM" line at the end is harmless.

---

## 7. Known limitations of local testing

- Method A cannot show that a transition is allowed, or that a service emits an event: only Method B can.
- Method B depends on the real clock (working hours) and has no switch to override it.
- Multi-replica behaviour (2–8 pods in AKS) is not reproducible with one Compose container; the claim query's
  concurrency is covered at database level only.
- Lease recovery after a crash needs `docker kill` of the service mid-attempt against a slow receiver; no automated
  test covers it.
- Hosted checks: use a test merchant on the AKS environment with your own webhook.site endpoint and real orders; never
  publish to the hosted broker. See [the operations runbook](../../../docs/deployment/OPERATIONS-RUNBOOK.md).
