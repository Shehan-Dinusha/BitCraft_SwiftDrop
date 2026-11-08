# Part 2: Your verification and viva guide

Use this guide yourself after checking out the Part 2 branch. Automated tests use disposable containers and are the main acceptance check. The optional manual exercise uses a separate demo database and RabbitMQ virtual host so it does not add synthetic metrics to your everyday analytics data.

Read [the implementation explanation](part-2-event-reliability.md) for the design and file responsibilities. Detailed generated test output stays local under ignored `backend/analytics-ai-service/target/`; no raw evidence folder needs to be pushed.

## 1. Confirm your tools and branch

At the repository root in PowerShell:

```powershell
git branch --show-current
git status --short
java -version
mvn -version
docker version
```

Use Java 25 and Maven 3.9+. Start Docker Desktop and confirm its server is reachable. Internet access may be needed for the first dependency/image downloads. You do not need to start the full Compose application to run Testcontainers tests.

## 2. Run the complete Part 2 acceptance suite

```powershell
mvn -f backend/pom.xml -pl analytics-ai-service -am verify
$LASTEXITCODE
```

Expect BUILD SUCCESS and exit 0. Ordinary unit/controller tests execute under Surefire; `TelemetryConsumptionIT` executes under Failsafe against real PostgreSQL and RabbitMQ. Docker must be available: this suite deliberately does not silently skip the integration tests.

At this Part 2 revision, expect 44 analytics unit/controller/config tests and 11 integration tests, plus 1 shared-contract test in its own reactor module. Future changes can legitimately add tests; verify execution and outcomes rather than relying only on a historical count.

During intentional failure tests, ERROR/WARN messages mentioning `intentional test-only projection failure` or a rejected analytics event are expected. Judge the suite by its completed test results and assertions, not by whether every log line says INFO.

`mvn ... test` alone is insufficient for Part 2: it does not execute Failsafe integration tests.

## 3. Inspect the generated reports yourself

```powershell
Get-Content backend/analytics-ai-service/target/failsafe-reports/com.swiftdrop.analytics.TelemetryConsumptionIT.txt
Get-Content backend/analytics-ai-service/target/failsafe-reports/failsafe-summary.xml
Get-ChildItem backend/analytics-ai-service/target/surefire-reports -Filter '*.txt'
```

Check that integration tests actually ran, with zero failures, errors, and skipped tests. Verify the report modification time matches your run. An old report from another revision is not evidence for your current changes.

Open `TelemetryConsumptionIT.java` and connect each requirement to a test:

| Requirement | Test method |
|---|---|
| Duplicate through a real broker | `brokerDuplicateHasOneDatabaseEffectAndPreservesMetadata` |
| Concurrent duplicate | `concurrentTransactionsForSameEventHaveOneEffect` |
| Atomic rollback and retry | `rollbackRemovesMarkerAndProjectionThenSameEventCanRetry` |
| Transient failure recovers | `transientDatabaseFailureSucceedsOnThirdBrokerAttempt` |
| Permanent failure is bounded, then replayable | `permanentDatabaseFailureIsDeadLetteredAfterThreeAttemptsAndCanBeReplayed` |
| Missing ID/malformed input | `missingStableIdAndMalformedSupportedJsonGoToDlqWithoutEffects` |
| Unsupported event does not block the queue | `unsupportedDispatchEventIsAcknowledgedWithoutProjectionOrDlq` |
| Assignment duplicate | `assignmentRedeliveryCountsOnce` |
| Creation binding and retained data | `orderCreatedIsBoundAndRetainedOnceForFutureProjectionWithoutUnknownFields` |
| Durable deduplication after restart | `realApplicationRestartDoesNotForgetProcessedIds` |
| V2 upgrade preserves old data | `upgradeFromV2PreservesExistingRowsAndAddsInbox` |

The application context starts on a fresh PostgreSQL database before those tests, exercising V1-V3 from scratch. The upgrade test uses another schema in the disposable database, not your local Compose volume.

## 4. Understand what the database assertions prove

Follow these operations in the code:

1. `TelemetryEventDecoder` rejects a missing event ID before constructing the shared event.
2. `TelemetryProcessingService.process` starts the database transaction.
3. The repository performs `INSERT ... ON CONFLICT DO NOTHING`.
4. A new ID applies the existing projection; a duplicate returns without applying it.
5. Spring commits before the listener completes and acknowledges the message.
6. A failure rolls back both the registration and projection.

In the concurrency test eight worker threads enter separate Spring-managed transactions with the same ID. The unique primary key, not a Java synchronized block or an exists check, guarantees one winner across multiple service instances too.

In the retry tests, the trigger fails actual PostgreSQL INSERTs. A sequence counts attempted writes even when transactions roll back. This proves that a third attempt really happened and that a fourth was not needed before dead-lettering.

## 5. Check declaration parity

Read `RabbitMQConfig.java` and the analytics entries in `infra/local/rabbitmq/definitions.json` side by side:

- Main queue: durable, quorum, 86,400,000 ms TTL.
- Main dead-letter exchange/key: `swiftdrop.dlx.exchange` / `dlq.analytics.events`.
- DLQ: durable, quorum, 604,800,000 ms TTL.
- Main bindings: `parcel.status.changed.#`, `dispatch.#`, `order.created`.

`RabbitMQConfigTest` checks those arguments/bindings automatically. The integration test imports the definitions before analytics starts, so a redeclaration incompatibility also fails startup.

If your everyday broker has a different historical queue type, do not delete it to make this exercise pass. Coordinate a rollout with M9. Queue types cannot be changed just by redeclaring a queue.

## 6. Optional manual demo: create isolated storage

This section is for observing the same behavior yourself in RabbitMQ Management and pgAdmin. It is not necessary to rerun it after every passing build. It creates a demo database/vhost and retains them for your viva.

Start the local infrastructure:

```powershell
docker compose up -d rabbitmq analytics-postgres
docker compose ps rabbitmq analytics-postgres
```

Create a new database and virtual host once:

```powershell
docker compose exec -T analytics-postgres psql -U swiftdrop -d postgres -c "CREATE DATABASE swiftdrop_analytics_part2_demo OWNER swiftdrop;"
docker compose exec -T rabbitmq rabbitmqctl add_vhost m10-part2-demo
docker compose exec -T rabbitmq rabbitmqctl set_permissions -p m10-part2-demo swiftdrop '.*' '.*' '.*'
```

If the demo database/vhost already exists, reuse it; do not delete an existing database to repeat this guide. Substitute your configured local username if it differs from `swiftdrop`.

In a separate application terminal at the repository root:

```powershell
mvn -f backend/pom.xml -pl common-contracts -am -DskipTests install
$env:DB_URL = 'jdbc:postgresql://localhost:5440/swiftdrop_analytics_part2_demo'
$env:SPRING_RABBITMQ_VIRTUAL_HOST = 'm10-part2-demo'
mvn -f backend/analytics-ai-service/pom.xml spring-boot:run
```

Other credentials use your existing analytics defaults/environment. These two overrides apply only to this terminal. Look for Flyway applying/validating V1-V3, and no `PRECONDITION_FAILED` queue argument errors. If port 8089 is occupied, use another terminal-local `SERVER_PORT` value for this demo.

Open RabbitMQ Management at `http://localhost:15672`, select virtual host **m10-part2-demo**, and inspect the two analytics queues. Connect pgAdmin to host `localhost`, port `5440`, database **swiftdrop_analytics_part2_demo**. Keep the demo vhost/database selected throughout this exercise.

## 7. Publish the exact same event twice

In your control PowerShell terminal, obtain your local RabbitMQ credentials without writing them into a file:

```powershell
$rabbitCredential = Get-Credential -UserName swiftdrop -Message 'Enter your local RabbitMQ password'
$event = @{
    eventId = [guid]::NewGuid().ToString()
    correlationId = [guid]::NewGuid().ToString()
    orderId = [guid]::NewGuid().ToString()
    trackingNumber = 'SD-M10-PART2-DEMO'
    newPublicStatus = 'DELIVERED'
    district = 'COLOMBO'
    timestamp = [DateTime]::UtcNow.ToString('o')
}
$publishBody = @{
    properties = @{ content_type = 'application/json' }
    routing_key = 'parcel.status.changed'
    payload = ($event | ConvertTo-Json -Compress)
    payload_encoding = 'string'
} | ConvertTo-Json -Depth 5
$publishUrl = 'http://localhost:15672/api/exchanges/m10-part2-demo/swiftdrop.events.exchange/publish'

Invoke-RestMethod -Method Post -Uri $publishUrl -Credential $rabbitCredential -ContentType 'application/json' -Body $publishBody
Invoke-RestMethod -Method Post -Uri $publishUrl -Credential $rabbitCredential -ContentType 'application/json' -Body $publishBody
$event.eventId
```

Expect `routed: true` for both publishes. You must reuse the same `$publishBody`; rebuilding `$event` would create a new event ID and test a different requirement.

In pgAdmin Query Tool, inspect:

```sql
SELECT event_id, event_type, order_id, correlation_id, occurred_at, processed_at
FROM processed_telemetry_events
ORDER BY processed_at;

SELECT district, snapshot_date, total_parcels, delivered_on_time
FROM operational_sla_snapshots
ORDER BY snapshot_date, district;

SELECT version, script, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

For a newly created demo database, expect one inbox row and one legacy parcel increment, despite two deliveries. If reusing the demo, compare before/after values and locate your event ID; do not assume total table counts are one.

Do not use the KPI GET as the verification target: its legacy sample/no-data behavior is intentionally deferred to Part 3. The existing column name `delivered_on_time` is also not evidence of an SLA calculation.

## 8. Restart analytics and publish the same body again

Stop only the demo application with Ctrl+C, then rerun its Maven startup command in the same terminal. The demo DB/vhost overrides remain set there. Keep the control terminal with `$publishBody` open.

Republish once from the control terminal:

```powershell
Invoke-RestMethod -Method Post -Uri $publishUrl -Credential $rabbitCredential -ContentType 'application/json' -Body $publishBody
```

Refresh the same database SELECTs. The existing event must still have one row and must not add another parcel increment. The service has forgotten its Java memory, but PostgreSQL remembers the ID.

## 9. Observe invalid input moving to the DLQ

Prepare an invalid copy without changing your valid replay body:

```powershell
$invalidEvent = $event.Clone()
$invalidEvent.Remove('eventId')
$invalidBody = @{
    properties = @{ content_type = 'application/json' }
    routing_key = 'parcel.status.changed'
    payload = ($invalidEvent | ConvertTo-Json -Compress)
    payload_encoding = 'string'
} | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post -Uri $publishUrl -Credential $rabbitCredential -ContentType 'application/json' -Body $invalidBody
```

In RabbitMQ Management, open `dlq.analytics.events.queue` in **m10-part2-demo**. Expect the invalid message there, with a rejected `x-death` entry, and no extra database row/increment. To inspect it without consuming the evidence, use the UI's requeue option when fetching messages.

You can repeat with payload `not-json` under the same supported routing key. It should be rejected rather than endlessly requeued. To test an unsupported key, publish `dispatch.failed`; it should be acknowledged without a new inbox row or DLQ message. A dispatch failure does not mean a delivered parcel failed.

Use the automated trigger tests for database failure/retry-limit demonstrations. Do not install failure triggers into your normal development database.

## 10. Finish and review your commit scope

Stop the demo application with Ctrl+C. Close its terminal or remove only its demo overrides before normal development:

```powershell
Remove-Item Env:DB_URL
Remove-Item Env:SPRING_RABBITMQ_VIRTUAL_HOST
```

Keep the demo database/vhost for your viva if useful. Stop infrastructure only if no other work needs it. Never use `docker compose down -v` to finish these checks.

Before committing:

```powershell
git diff --check
git status --short
git diff --stat
```

Review new files as well as tracked diffs. Commit implementation, tests, V3, the additive broker binding, and explanatory Markdown. Leave `.env`, credentials, `target/`, build logs, and local test reports out. No commits or pushes are performed by the agent.

Suggested small commit groups:

1. `build(analytics): configure broker and database integration tests`
2. `fix(analytics): process telemetry events idempotently`
3. `fix(analytics): align queue declarations and failure handling`
4. `docs(m10): explain event reliability and self-verification`

Group dependent code/tests so each commit compiles; run the full verification on the final branch. Request M9 review for `definitions.json`. Open the PR into `dev` as `fix(analytics): make telemetry processing duplicate-safe`.

## Viva questions and answers

| Question | What to explain |
|---|---|
| Why does RabbitMQ send duplicates? | A consumer can commit work and lose its connection before acknowledgement. Redelivery is normal in at-least-once systems. |
| Why validate before constructing records? | Shared record constructors generate defaults for missing IDs/times. That could turn an invalid redelivery into a different event. |
| Why not check exists first? | Two instances can both observe absence. PostgreSQL's primary key and atomic conflict-handling insert arbitrate concurrent processing. |
| Why one transaction? | Committing the marker without effects loses work; committing effects without the marker permits duplicates. Both must commit or roll back together. |
| How does JDBC share a transaction with JPA? | Spring's transaction manager binds the same datasource connection to the thread; JdbcTemplate participates while JPA performs its projection writes. |
| Why a separate processing service? | Calling it through Spring's bean proxy applies `@Transactional`; a plain self-call would bypass proxy interception. |
| Is this exactly-once delivery? | No. Messages may be delivered repeatedly, but the same stable ID has at most one committed database effect in this service. |
| Why retain order.created without incrementing totals? | The legacy status event already increments totals. Creation data is retained for Part 3's proper created-per-day projection, avoiding a second legacy increment now. |
| What is a quorum queue? | A RabbitMQ queue type designed for replicated durable messaging. Producer/consumer declarations must match its configured type and arguments. A single-node local broker does not prove cluster failover. |
| What happens to invalid messages? | Immediate rejection without requeue sends them through the configured DLX to the DLQ. Storage failures get up to three attempts first. |
| Why ignore dispatch.failed? | It is outside the current projection handlers. Explicit unsupported handling prevents classifying a dispatch matching failure as a delivery outcome. |
| What does the restart test prove? | It closes and starts the real application context against unchanged database/broker containers; duplicates remain ignored because IDs are durable. |
| Why does the test deliberately produce errors? | Database fault injection verifies rollback, retry count, and DLQ recovery; final assertions establish correctness. |
| What does Part 2 not solve? | Distinct IDs for repeated business states, correct daily/courier metrics, new authorized APIs, and AI behavior. Those require later parts. |
