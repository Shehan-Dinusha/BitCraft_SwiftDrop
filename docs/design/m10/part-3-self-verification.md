# Part 3: Your verification, commits, viva, and team update

Use these instructions yourself. The [implementation explanation](part-3-analytics-projections.md) describes the architecture and limitations. This part implements projections and corrects existing compatibility reads; the new daily/rate/workload HTTP routes remain Part 4 and charts remain Part 8.

## 1. Confirm the branch and run the tests

From the repository root in PowerShell:

```powershell
git branch --show-current
git status --short
java -version
mvn -version
docker version
mvn -f backend/pom.xml -pl analytics-ai-service -am clean verify
```

Start Docker Desktop first. Expect BUILD SUCCESS. At this revision there are 43 analytics unit/controller/config tests, 23 real PostgreSQL/RabbitMQ integration tests, and 1 shared-contract test in its separate module. Expect zero failures, errors, and skips. Deliberate database-failure/rejection logs are expected inside the retry tests.

Inspect current reports, not stale reports from an earlier run:

```powershell
Get-Content backend/analytics-ai-service/target/failsafe-reports/com.swiftdrop.analytics.TelemetryConsumptionIT.txt
Get-Content backend/analytics-ai-service/target/failsafe-reports/failsafe-summary.xml
```

`mvn ... test` alone does not execute the integration suite. The containers are disposable; tests do not reset your everyday PostgreSQL database.

Open `backend/analytics-ai-service/target/site/jacoco/index.html` for your local coverage report. The recorded clean run covered 93.08% of service-package lines and 100% of controller-package lines. Keep current evidence for submission; the report changes when you rerun tests or change code.

## 2. Understand the tests you are demonstrating

| Scenario | Integration test |
|---|---|
| Four-order totals, distinct-ID repeated status, old status, completion | `fourOrdersProduceExactDailyOutcomesAndCourierWorkload` |
| Status before assignment and delayed reassignment credit | `delayedAssignmentsReconcileOutcomeCourierWithoutRecountingDelivery` |
| Colombo midnight including nanosecond input | `colomboMidnightAndNanosecondPrecisionDoNotMoveOrdersToWrongDay` |
| Final failures, cancellation, and return distinction | `cancellationsAndReturnsDoNotBecomeAdditionalFailures` |
| Contradictory terminal histories and equal-time statuses | `conflictingOutcomesAreVisibleAndExcludedInsteadOfDoubleCounted` |
| Different orders processed concurrently | `concurrentDifferentOrdersDoNotLoseDailyIncrements` |
| Old Part 2 inbox catch-up and another restart | `startupBackfillsPart2InboxAndDoesNotRepeatItOnNextRestart` |
| No sample insertion or legacy sample reuse | `emptyReadsNeverSeedTablesOrReuseLegacySamples` |
| Assignment moving between dates and competing couriers | `reassignmentMovesCurrentWorkloadBetweenAssignmentDates` |
| Weighted rate, zero-filled dates, and range validation | `rangeRatesUseSummedOutcomesInsteadOfAverageDailyPercentages` |
| Creation and delivery on separate days | `creationAndOutcomeAreIndependentFlowsOnTheirOwnDates` |
| Existing V3 inbox survives V4 and becomes pending | `v3UpgradeRetainsInboxPayloadAndMarksItForCatchup` |

The other eleven integration tests continue testing Part 2's duplicate, rollback, retry, DLQ, restart, and upgrade behavior against the corrected projection path.

## 3. Prepare an isolated manual demo

Start only the required infrastructure:

```powershell
docker compose up -d rabbitmq analytics-postgres
docker compose ps rabbitmq analytics-postgres
```

Create demo storage once, separate from normal data:

```powershell
docker compose exec -T analytics-postgres psql -U swiftdrop -d postgres -c "CREATE DATABASE swiftdrop_analytics_part3_demo OWNER swiftdrop;"
docker compose exec -T rabbitmq rabbitmqctl add_vhost m10-part3-demo
docker compose exec -T rabbitmq rabbitmqctl set_permissions -p m10-part3-demo swiftdrop '.*' '.*' '.*'
```

If they already exist, reuse them and inspect existing demo data rather than deleting it. Expected whole-database counts below assume a new demo or the same date's replay. Older demo dates can retain active orders, so account for those if reusing the database across days. Use your configured username if not `swiftdrop`.

In a new application terminal:

```powershell
mvn -f backend/pom.xml -pl common-contracts -am -DskipTests install
$env:DB_URL = 'jdbc:postgresql://localhost:5440/swiftdrop_analytics_part3_demo'
$env:SPRING_RABBITMQ_VIRTUAL_HOST = 'm10-part3-demo'
mvn -f backend/analytics-ai-service/pom.xml spring-boot:run
```

Credentials use existing analytics configuration/environment. Keep this terminal open. Expect migrations through V4, successful startup, and no queue-declaration errors. V4 must not modify V1-V3 or delete old tables.

## 4. Check the empty state first

In a control terminal:

```powershell
Invoke-RestMethod http://localhost:8089/actuator/health
Invoke-RestMethod http://localhost:8089/api/v1/analytics/kpis | ConvertTo-Json -Depth 6
Invoke-RestMethod http://localhost:8089/api/v1/analytics/couriers | ConvertTo-Json -Depth 6
```

On fresh storage expect UP, zero real counts, an empty courier list, and null SLA/transit measurements. The old empty-database read-only INSERT failure should be gone. Direct port 8089 is diagnostic; browser traffic still belongs through the gateway.

Open `http://localhost:8089/swagger-ui.html`. Inspect the KPI/courier schema and verify unavailable measurements allow null. Do not confuse a null on-time rate with a zero success rate: they measure different things, and SLA is not measured here.

## 5. Publish the controlled dataset

Run the fixture publisher from the repository root. It will prompt for your local RabbitMQ credentials:

```powershell
& ./docs/design/m10/fixtures/publish-part3-demo.ps1
```

If PowerShell blocks local scripts, review the script first, then use a separate process with a process-only execution policy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./docs/design/m10/fixtures/publish-part3-demo.ps1
```

This does not permanently change the machine's execution policy. The script accepts only a vhost named `m10-part3-...`, publishes synthetic facts, and does not read another service's database. It never runs automatically with analytics.

Fifteen events describe four orders:

| Order | History | Expected result |
|---|---|---|
| A | Created, assigned C1, delivered; repeated delivered with another ID and an older transit event | One creation and one delivery, no active workload |
| B | Created, assigned C1, delivered | One creation and one delivery, no active workload |
| C | Created, assigned C2, FAILED | One creation and one failure, no active workload |
| D | Created, assigned C1, reassigned C2, in transit | One creation, currently active under C2 |

Dates are today's Colombo date by default; event IDs are deterministic for that date. Rerunning the same day's fixture reuses the same IDs. You can supply `-ReportDate '2026-11-08'` to reproduce a historical date; the legacy KPI GET still reads today's counts, so use date-filtered SQL for historical fixtures.

## 6. Inspect RabbitMQ and PostgreSQL yourself

Open RabbitMQ Management `http://localhost:15672`, select **m10-part3-demo**, and inspect the analytics consumer/queues/bindings. Messages should drain from the main queue; valid fixture messages should not enter the DLQ.

In pgAdmin connect to host `localhost`, port `5440`, database **swiftdrop_analytics_part3_demo**, with your local PostgreSQL credentials. Run:

```sql
SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT * FROM daily_delivery_stats ORDER BY report_date;

SELECT round(100.0 * sum(delivered) / nullif(sum(delivered + failed), 0), 2) AS success_rate_percent
FROM daily_delivery_stats;

SELECT courier_id, sum(delivered) delivered, sum(failed) failed, sum(active) active
FROM courier_daily_stats GROUP BY courier_id ORDER BY courier_id;

SELECT order_id, current_status, courier_id, outcome, outcome_courier_id, conflict_reason
FROM analytics_order_projection ORDER BY order_id;

SELECT count(*) accepted_events,
       count(*) FILTER (WHERE NOT projection_applied) pending_projection
FROM processed_telemetry_events;
```

Expected for the fresh fixture:

- Created **4**, delivered **2**, failed **1**, success **66.67%**.
- C1 delivered **2**, failed **0**, active **0**.
- C2 delivered **0**, failed **1**, active **1**.
- Four order-state rows; no conflicts.
- Fifteen accepted event IDs; zero pending projection rows.

The script prints C1/C2 UUIDs so you can match courier rows. `active` must be summed across dates: the per-day bucket is the effective assignment date, not historical end-of-day availability.

## 7. Compare the existing HTTP responses

```powershell
Invoke-RestMethod http://localhost:8089/api/v1/analytics/kpis | ConvertTo-Json -Depth 6
Invoke-RestMethod http://localhost:8089/api/v1/analytics/couriers | ConvertTo-Json -Depth 6
```

For today's fixture, expect `totalParcels: 4`, `failedDeliveries: 1`, and `activeCouriers: 1`. C1 should show two deliveries and `NO_ACTIVE_ORDERS`; C2 one failure and `BUSY`. Assignment totals are distinct orders ever assigned: C1 has three, C2 two.

SLA fields remain null. In particular, **do not expect deliveredOnTime to equal 2**: two parcels were delivered, but no SLA deadline proves they were on time. Dedicated rate/daily/workload HTTP endpoints arrive in Part 4.

District breakdown counts creations by pickup district and failures by outcome district. In this fixture Colombo has four creations while Kandy has one failure; this is expected, not a lost count.

## 8. Prove replay and restart behavior

Run the same fixture again. Refresh the SQL/API results. Totals must remain unchanged, including fifteen accepted IDs.

Stop only the demo application with Ctrl+C, restart it in the same application terminal, and run the fixture again. The database should still show the same results. Do not truncate the new tables to repeat this test; that would discard the evidence you are testing.

The automated suite additionally simulates old accepted Part 2 events with pending flags and proves startup catch-up materializes them once. Manual insertion of synthetic inbox rows or failure triggers is unnecessary in your everyday database.

## 9. Frontend and gateway checks

Regenerated analytics types must compile with the existing UI:

```powershell
Push-Location frontend
npm.cmd run lint
npm.cmd run build
Pop-Location
```

Both should pass. The existing bundle-size warning is nonfatal. No dashboard charts were implemented in this part, so a placeholder Analytics page is still expected. The Fleet Control Tower still contains its older SLA display fallbacks; do not present those percentages as evidence of this backend's projections.

If you want to inspect documentation through the normal gateway, start Redis and the gateway in another terminal:

```powershell
docker compose up -d redis
mvn -f backend/api-gateway/pom.xml spring-boot:run
```

Then:

```powershell
curl.exe -i http://localhost:8080/api/v1/analytics/v3/api-docs
curl.exe -i http://localhost:8080/api/v1/analytics/kpis
```

Expect documentation 200 and KPI 401 without a token. Authenticated reporting RBAC belongs to Part 4. Gateway routes/identity rules were not changed by Part 3.

## 10. Suggested commits

No commits or pushes are performed by the agent. Run each command yourself after reviewing the staged diff. Start with `git status` and ensure only your Part 3 changes are present.

First, the calculation/state foundation:

```powershell
git add backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/domain/projection
git add backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/domain/repository/AnalyticsProjectionRepository.java
git diff --cached
git commit -m "feat(analytics): define event-time order and courier projections"
```

Then connect the migration, pipeline, read adapters, tests, and their synchronized contracts. Keep nullable DTOs, specification, and generated types together:

```powershell
git add backend/analytics-ai-service/src/main
git add backend/analytics-ai-service/src/test
git add docs/api-specs/analytics-ai-service.yaml
git add frontend/src/types/generated/analytics-ai-service.ts
git diff --cached
git commit -m "feat(analytics): materialize daily metrics and replace sample KPI reads"
```

Finally the explanation/demo documentation:

```powershell
git add docs/design/m10/README.md docs/design/m10/implementation-roadmap.md
git add docs/design/m10/reporting-contracts.md docs/design/m10/handoff-requests.md
git add docs/design/m10/part-3-analytics-projections.md docs/design/m10/part-3-self-verification.md
git add docs/design/m10/fixtures/publish-part3-demo.ps1
git diff --cached
git commit -m "docs(m10): explain projections and add repeatable demo guide"
```

The connected behavior and its regression tests are coupled. If your reviewer requires smaller commits, use VS Code's Stage Selected Ranges to keep the existing integration-test adapter changes with the runtime change and stage the additional scenarios separately; do not create a commit whose tests still reference removed legacy methods/tables.

Before pushing:

```powershell
git diff --check
git status
git log -3 --oneline
git push -u origin HEAD
```

Open the PR into `dev`: **feat(analytics): build daily delivery and courier projections**. Explain the compatibility nulls and request relevant frontend/member review. Keep `.env`, credentials, `target/`, verbose logs, and unrelated generated files out. Retain curated evidence for the member guide's final submission requirement.

Before reviewing the PR's scope, run `git fetch origin` and `git diff --stat origin/dev...HEAD`. Ensure the Part 2 dependency is merged into `dev`, or explicitly identify that dependency; otherwise the comparison may include both parts. Do not claim a merge based only on your local branch name.

## 11. Viva points and team update

| Question | Brief explanation |
|---|---|
| What did Part 3 add beyond Part 2? | Event IDs prevent transport duplicates; per-order reconciliation also prevents distinct status events from multiplying one order outcome. |
| Why reconcile history? | Late facts can correct assignment attribution and reporting dates without depending on arrival order. |
| Why store both UTC times and Colombo dates? | UTC identifies the instant; the explicit reporting zone determines the business day. |
| How does reassignment work? | Subtract the old order contribution and add the new one atomically. Completed orders contribute no active workload. |
| How is concurrency handled? | A PostgreSQL transaction advisory lock coordinates replicas and catch-up; atomic counter deltas commit with source registration. It trades throughput for simple correctness. |
| Why are rates nullable? | No outcomes means an undefined success ratio. No SLA evidence means unavailable on-time measurements, not fabricated percentages. |
| Why do conflicts reduce counts? | Contradictory evidence is retained visibly and excluded rather than counted twice or resolved arbitrarily. |
| What happens on upgrade? | V4 marks old inbox facts pending. Startup catch-up rebuilds their order contributions once; legacy samples are ignored. |
| What remains? | Part 4's date-filtered HTTP reports/RBAC, later AI work, charts/widget, and final diagrams. |

Suggested group update:

> Part 3 is implemented locally: event-time daily delivery counts, courier outcome/workload projections, reassignment and late-event reconciliation, and startup catch-up from the reliable inbox. Sample KPI insertion and invented backend SLA defaults are removed. The controlled four-order dataset produces 4 created, 2 delivered, 1 failed, 66.67% success and 1 active order. New reporting routes/RBAC and dashboard charts are still pending. M5/M6 semantics and compatibility changes remain documented for review.

Stop applications with Ctrl+C after your checks. Demo overrides belong only in the application terminal; close it or remove `Env:DB_URL` and `Env:SPRING_RABBITMQ_VIRTUAL_HOST` there before normal development. Preserve database volumes; do not run `docker compose down -v` to finish verification.
