# Part 2: Reliable analytics event consumption

## Scope

Implemented on `Buddhika/feat/event-reliability`, starting from `614fe02`. Part 2 protects the existing analytics database effects from delivery of the same event more than once. It does not yet correct the legacy metric definitions, replace sample KPI defaults, or build daily/per-order projections; those remain Part 3.

No REST path, response DTO, shared event record, environment variable, or frontend contract changes are needed here. The shared broker change is additive: one analytics binding for `order.created`. M9 review is required for that shared file before merging; no approval or external message is implied by this implementation.

## Processing path

```text
RabbitMQ delivery (raw bytes + received routing key)
  -> TelemetryEventDecoder
       unsupported key: acknowledge without a projection
       invalid supported event: reject without requeue -> DLQ
       valid supported event: validated shared event record
  -> TelemetryProcessingService (database transaction)
       INSERT inbox event ON CONFLICT DO NOTHING
       duplicate ID: return successfully, no projection effect
       new ID: apply existing status/assignment effect, or retain order.created
       commit inbox + projection together
  -> listener returns successfully -> automatic broker acknowledgement
```

An application crash after the database commit but before the broker acknowledgement can cause redelivery. The existing primary key then prevents the second database effect. This is at-least-once message delivery with idempotent database processing, not an exactly-once broker guarantee.

## Files and their responsibilities

| File under `backend/analytics-ai-service` | Responsibility and reason |
|---|---|
| `src/main/resources/db/migration/V3__add_processed_telemetry_events.sql` | Adds the durable inbox, with producer event ID as primary key and canonical payload retained for later backfill. V1/V2 remain immutable. |
| `messaging/TelemetryEvent.java` under the Java package | Internal wrapper for a validated shared event and its identifiers/time; not a new public event contract. |
| `messaging/TelemetryEventDecoder.java` | Selects the shared record by routing key and validates required values before shared constructors generate defaults. |
| `domain/repository/ProcessedTelemetryEventRepository.java` | Performs an atomic PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`; no race-prone exists-then-insert. |
| `service/TelemetryProcessingService.java` | Transaction boundary containing both JDBC registration and existing JPA updates. It is a separate Spring bean so the transaction proxy is actually invoked. |
| `messaging/consumer/TelemetryAggregationConsumer.java` | Receives raw AMQP `Message`, invokes decoder/processor, and deliberately ignores unsupported routing keys. |
| `config/RabbitMQConfig.java` | Aligns quorum, TTL, DLX and bindings; configures raw-message conversion and bounded retry/rejection behavior. |
| `pom.xml` | Adds Testcontainers dependencies and activates the parent-managed Failsafe goals, including failure if no integration tests are found. |
| Consumer/config unit tests and `TelemetryConsumptionIT.java` | Validate malformed input, declaration parity, real database transactions, real broker behavior, and restart persistence. |

Java paths in the table are relative to `src/main/java/com/swiftdrop/analytics/` unless otherwise stated.

## Inbox schema and transaction

`processed_telemetry_events` contains:

| Column | Purpose |
|---|---|
| `event_id` (UUID primary key) | Stable producer identity; durable duplicate protection |
| `event_type` | Supported routing key used to identify the event |
| `order_id` | Business correlation for future order projections |
| `correlation_id` | Original trace correlation, preserved rather than regenerated |
| `occurred_at` | Original producer timestamp as a timezone-aware instant |
| `payload` (JSONB) | Canonical shared event, not arbitrary raw JSON; unknown input fields are not retained |
| `processed_at` | Time the database transaction registered the event |

PostgreSQL handles simultaneous inserts of the same event ID through the primary-key constraint. The competing insert waits for the first transaction, then either does nothing after its commit or succeeds after its rollback. This avoids catching a unique-constraint exception inside an already failed transaction.

Spring's datasource transaction is shared by `JdbcTemplate` and JPA. If the projection write fails, the inbox insert also rolls back. A retry can register the same ID again and complete. Do not add `REQUIRES_NEW` around either operation.

Keep inbox records while their events may be replayed. Automatically expiring IDs would shorten the duplicate-protection window. Retention and historical backfill policy are future operational decisions.

## Supported events and validation policy

| Exact routing key | Validated business data | Part 2 action |
|---|---|---|
| `parcel.status.changed` | Stable event/order/correlation UUIDs, timestamp, tracking number, resolved public status, district | Register and invoke existing delivery metric update |
| `dispatch.courier.assigned` | Common identifiers/time, courier UUID, nonblank name <=255 characters, assignedAt, nonnegative estimated pickup minutes | Register and invoke existing assignment metric update |
| `order.created` | Common identifiers/time, tracking number, pickup and delivery districts | Register and retain canonical event for Part 3; no second legacy parcel increment |

Required UUIDs must use canonical UUID string format. Required times must be parseable textual UTC/offset instants. Malformed JSON, duplicate JSON keys, trailing root documents, missing identifiers, invalid enums, and missing required business values are rejected.

The parcel event's existing `traceId`/`newStatus` aliases and internal-status-to-public mapping remain supported through the shared contract. Missing event ID or occurrence time is not repaired using a newly generated value. Existing publishers supply UUIDs and times, but their owners must still preserve them when republishing an event.

Only the exact supported keys above are processed. The queue retains the existing broad bindings `dispatch.#` and `parcel.status.changed.#`; other keys delivered by those bindings, including `dispatch.failed` or future suffixed versions, are deliberately acknowledged without effects. A new key requires an explicit decoder handler and tests. A dispatch matching failure is not automatically a delivery failure.

The listener uses a simple byte/string converter instead of producer-controlled Java `__TypeId__` conversion. The decoder selects only the three shared record types above. It does not infer event type from arbitrary JSON fields. Exceptions do not contain parser snippets or raw input; the recoverer avoids logging the rejected body.

## Broker declarations and failure handling

| Resource | Declaration |
|---|---|
| Events exchange | Durable topic `swiftdrop.events.exchange` |
| Main queue | Durable quorum `analytics.events.queue`; TTL 86,400,000 ms (24 hours) |
| Dead-letter destination | `swiftdrop.dlx.exchange`, key `dlq.analytics.events` |
| DLQ | Durable quorum `dlq.analytics.events.queue`; TTL 604,800,000 ms (7 days) |
| Main bindings | `parcel.status.changed.#`, `dispatch.#`, newly added `order.created` |

The Java arguments now match `infra/local/rabbitmq/definitions.json`. Existing correctly declared quorum queues can be redeclared without deleting messages or volumes. A differently configured old queue still needs an owner-reviewed migration; do not delete shared queues to suppress errors.

Invalid supported events are nonretryable and rejected immediately. Other processing failures receive at most three attempts per listener delivery, with 1 second and 2 second delays between attempts. Every attempt calls the transactional service again. After exhaustion, rejection without requeue sends the original message to the DLQ. Unsupported events are acknowledged, not retried or sent to DLQ.

These retries are in-process. A process crash can restart an attempt sequence; the durable inbox still protects committed effects. Permanent storage outages require operator recovery and replay from DLQ after the fault is fixed. Do not invent a replacement ID for an invalid event without reconciling with the producer.

## Integration-test design

`TelemetryConsumptionIT` starts disposable PostgreSQL 16 and RabbitMQ 3.13 containers, imports the repository broker definitions, and starts the actual analytics application context. It does not mock the database, broker, repositories, or processing service. A dedicated test broker user is created through the container CLI; no production credential is changed. Docker absence is a test failure, not a silent skip.

The tests exercise:

1. The same status through RabbitMQ twice: one inbox row and one legacy database increment; producer IDs preserved; untrusted Java type header ignored.
2. Eight simultaneous transactions with the same ID: one database effect.
3. A PostgreSQL trigger failing after a projection INSERT: both marker and projection roll back; the same ID succeeds after removing the fault.
4. Two transient database failures: third broker attempt succeeds, no DLQ message.
5. Permanent database failure: exactly three attempts, no committed marker/effect, then DLQ; replay succeeds after removing the fault.
6. Missing stable ID and malformed supported JSON: DLQ, no fabricated IDs or metrics.
7. Unsupported dispatch event: acknowledged; a subsequent supported event still processes.
8. Assignment redelivery: one assignment increment.
9. Creation event binding and retention: one inbox row, no legacy metric increment, unknown field excluded.
10. Closing and restarting the actual application context against the same containers: old event remains ignored, new event still processes.
11. Fresh database startup through V1-V3 and a separate V2-to-V3 upgrade schema: existing rows survive the upgrade.

Failure-injection triggers and sequences exist only in disposable test storage. PostgreSQL sequences intentionally count attempts outside transaction rollback, allowing tests to prove the retry limit. No fault-injection switch or test endpoint is added to the application.

The configuration unit test compares Java queue arguments and bindings against the checked-in broker JSON so future drift fails a build.

## Verification and scope limits

Final verification on 2026-11-08 completed with BUILD SUCCESS:

| Check | Actual result |
|---|---|
| `mvn -f backend/pom.xml -pl analytics-ai-service -am verify` | 44 analytics unit/controller/config tests and 11 integration tests passed; zero failures/errors/skips. Common contracts also ran its 1 passing test. |
| Failsafe summary inspection | `completed=11`, `errors=0`, `failures=0`, `skipped=0`; report corresponds to the final run finishing at 11:14:25 +05:30. |
| `docker compose config --quiet` | Passed; no everyday Compose services were started by Part 2 verification. |
| Broker JSON and module POM parsing | Passed; analytics binding list includes all three intended keys. |

Integration tests used only disposable containers and automatically closed their application contexts. The optional manual demo in the guide is for you to perform; it was not run against your everyday database. No commit, push, external message, or team approval was performed.

Run `mvn -f backend/pom.xml -pl analytics-ai-service -am verify`, then inspect `backend/analytics-ai-service/target/failsafe-reports`. The companion [self-verification guide](part-2-self-verification.md) explains how to run this yourself and what each result means.

Raw build output and detailed test reports stay under ignored `target/`, following the preference to keep raw evidence local. Only the implementation and explanatory Markdown need to be committed.

The following remain intentionally unresolved for later parts:

- Different event IDs representing the same business status still require per-order state in Part 3.
- Legacy parcel totals, on-time labels, sample seeding, receipt-date grouping, and concurrency between different events are not corrected by same-ID deduplication.
- `order.created` is retained for later backfill; replaying it into this Part 2 consumer does not generate a daily report yet.
- No authenticated reporting API, frontend chart, real tracking/pricing client, or LLM integration is implemented here.
- M9's shared-file review and producer guarantees remain pending; implementation is not teammate approval.
