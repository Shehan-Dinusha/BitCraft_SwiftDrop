# Part 1: Your self-verification and viva guide

This guide is for you to run yourself. It explains what to check, why it matters, and what counts as success for Part 1. The earlier executed results are recorded separately in [the evidence report](../../test-reports/m10/part-1/README.md).

Part 1 is a baseline and design deliverable. Its success means you can reproduce the starting behavior and explain the proposed contracts. The current KPI and broker failures are expected baseline defects; fixing them belongs to later branches.

## 1. Confirm your branch and scope

Open PowerShell in the repository root:

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
git diff --stat
```

Your current feature branch should be shown. Compare the recorded starting revision in [baseline notes](baseline-notes.md); later commits can legitimately change HEAD. `git diff --stat` does not display untracked documents, so also use `git status`.

Review the new files under `docs/design/m10/` and `docs/test-reports/m10/part-1/`. There should be no Part 1 application changes to Java, TypeScript, migrations, or broker configuration. The roadmap was already created before this part and was untracked when this work began.

**Why:** A baseline must identify the revision being measured. Otherwise, comparing results after a later fix is ambiguous.

## 2. Check the required tools

```powershell
java -version
mvn -version
node --version
npm.cmd --version
docker --version
docker compose version
```

Expect Java 25, Maven 3.9+, Node 20+ and available Docker/Compose. Open Docker Desktop if the daemon is unavailable. In your normal terminal you should not need the coding agent's sandbox permissions.

**Why:** A dependency/toolchain failure is different from an application defect. The original sandbox Maven failure did not run any tests.

## 3. Run the backend baseline tests yourself

```powershell
mvn -f backend/pom.xml -pl analytics-ai-service -am test
$LASTEXITCODE
```

Expect exit code 0 and BUILD SUCCESS. At the recorded revision, analytics has 27 tests and common contracts has 1. Maven reports these separately per module; do not mistake the final analytics summary for the reactor-wide count.

Open `backend/analytics-ai-service/target/surefire-reports/`. It should contain reports for:

| Suite | Recorded count |
|---|---:|
| DeliveryAssistantAgentTest | 4 |
| AiDeliveryAssistantControllerTest | 5 |
| OperationalKpiControllerTest | 5 |
| TelemetryAggregationConsumerTest | 3 |
| AiDeliveryAssistantServiceTest | 4 |
| SlaMetricsAggregationServiceTest | 6 |

Open `SlaMetricsAggregationServiceTest.java` and find `getSystemKpiSummary_seedsDefaultsWhenEmpty`. It expects samples and 12 active couriers. Explain why this test can pass while the desired no-data contract is wrong. Mockito repositories do not enforce PostgreSQL read-only transactions.

**Pass criterion:** The existing tests execute and you record their real result. A new failure should be investigated and documented; do not change unrelated code just to match this report. This command is not proof of real PostgreSQL/RabbitMQ integration.

## 4. Run the frontend checks yourself

If dependencies are missing, run `npm.cmd ci` inside `frontend` first. Then:

```powershell
Push-Location frontend
npm.cmd run lint
# Check this result before running the next command.
$LASTEXITCODE
npm.cmd run build
$LASTEXITCODE
Pop-Location
```

Expect both exit codes to be zero. The existing large-chunk warning is nonfatal. Read the `lint` script in `frontend/package.json`: it invokes TypeScript checks, not ESLint.

**Why:** These establish that the starting UI compiles and type-checks. They do not prove charts, network integration, or accessibility; the analytics page is still a placeholder.

## 5. Start the analytics infrastructure

```powershell
docker compose config --quiet
docker compose up -d rabbitmq analytics-postgres
docker compose ps rabbitmq analytics-postgres
```

Expect the two containers to become healthy. PostgreSQL is exposed at 5440; RabbitMQ at 5672 and management UI at 15672. Use your actual local credentials if `.env` overrides defaults. Do not overwrite `.env` or delete volumes.

**Why:** Analytics owns this database. You do not need another member's database to inspect the analytics baseline.

## 6. Start analytics in its own terminal

First, at the repository root:

```powershell
mvn -f backend/pom.xml -pl common-contracts -am -DskipTests install
mvn -f backend/analytics-ai-service/pom.xml spring-boot:run
```

Keep this terminal open. If your ports or credentials differ, set the environment variables listed in [baseline notes](baseline-notes.md) first. Avoid the parent `-am spring-boot:run` startup form: parent/library projects are not runnable applications.

Look for Flyway validating two migrations, current schema version 2, and Tomcat on 8089. Then inspect the RabbitMQ messages. On the unchanged baseline, expect a declaration error mentioning:

```text
PRECONDITION_FAILED - inequivalent arg 'x-queue-type' for queue 'analytics.events.queue'
received none but current is the value 'quorum'
```

Compare `RabbitMQConfig.java` with `infra/local/rabbitmq/definitions.json`: the latter declares quorum and TTL arguments, the former does not. Do not delete/recreate the queue to hide this baseline defect.

**Why:** Queue declarations must match exactly. A running web server is compatible with a broken event listener.

## 7. Inspect Swagger and existing endpoints

Open `http://localhost:8089/swagger-ui.html` in a browser. Also run:

```powershell
Invoke-RestMethod http://localhost:8089/actuator/health
Invoke-RestMethod http://localhost:8089/api/v1/analytics/health
Invoke-RestMethod http://localhost:8089/api/v1/analytics/couriers
```

Expect UP, a controller readiness message, and whatever real courier projection exists in your database. At the recorded baseline, couriers were empty. Swagger schemas describe the interface; they do not verify the metrics.

Capture your own live document without overwriting the recorded evidence:

```powershell
$spec = Invoke-RestMethod http://localhost:8089/v3/api-docs
$spec.openapi
$spec.paths.PSObject.Properties.Name
$spec | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 "$env:TEMP/m10-my-openapi.json"
```

Expect OpenAPI 3.1.0 and 20 paths at this revision because aliases are included. Compare canonical paths with `docs/api-specs/analytics-ai-service.yaml`. Do not claim proposed `/deliveries/daily`, `/success-rate`, `/couriers/workload`, or `/ai/chat` already work.

For HTTP status and body inspection, use `curl.exe` explicitly to avoid PowerShell's `curl` alias:

```powershell
curl.exe -i http://localhost:8089/api/v1/analytics/couriers/00000000-0000-0000-0000-000000000001
curl.exe -i http://localhost:8089/api/v1/analytics/kpis
```

The unknown courier should give 404 unless your database happens to contain that UUID. On a database without today's snapshots, KPI GET currently gives 400 because it tries to insert inside a read-only transaction. If today's snapshots already exist, the result can differ: compare data and code rather than deleting your everyday records to force this error. Do not treat returned sample-like numbers as operational truth.

**Pass criterion:** You can explain and record the observed baseline, including failures. A valid new zero-state report is not a Part 1 acceptance test.

## 8. Inspect the database yourself

Run read-only queries:

```powershell
docker compose exec -T analytics-postgres psql -U swiftdrop -d swiftdrop_analytics -c "SELECT installed_rank, version, description, script, success FROM flyway_schema_history ORDER BY installed_rank;"
docker compose exec -T analytics-postgres psql -U swiftdrop -d swiftdrop_analytics -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"
docker compose exec -T analytics-postgres psql -U swiftdrop -d swiftdrop_analytics -c "SELECT count(*) FROM operational_sla_snapshots;"
```

Or connect pgAdmin to host `localhost`, port `5440`, database `swiftdrop_analytics`, user `swiftdrop`, and your configured password. Run the same SELECTs in Query Tool.

Expect V1 and V2 successful on the unchanged revision and four application tables plus Flyway history. Compare to [migration evidence](../../test-reports/m10/part-1/migrations.txt). Later branches will legitimately add new versions.

**Why:** Flyway history is the database's actual applied state; migration files alone do not prove they ran. Part 1 did not add a migration or prove fresh-database installation.

## 9. Check gateway routing separately from role authorization

In another terminal, for a fully provisioned local gateway:

```powershell
docker compose up -d redis
mvn -f backend/api-gateway/pom.xml spring-boot:run
```

Then:

```powershell
curl.exe -i http://localhost:8080/actuator/health
curl.exe -i http://localhost:8080/api/v1/analytics/v3/api-docs
curl.exe -i http://localhost:8080/api/v1/analytics/kpis
```

Expect documentation 200 and the request without credentials 401. Gateway aggregate health depends on configured dependencies; the earlier run returned 503 with Redis absent. Do not reinterpret that captured run as fully healthy.

Do not send fabricated identity headers as proof of admin access. M1 validates a real JWT and replaces untrusted headers. Authenticated DISPATCHER/SYSTEM_ADMIN success and CUSTOMER 403 belong to Part 4 after analytics RBAC is implemented.

**Why:** Authentication asks who you are; authorization asks whether your verified role can perform this operation. Today's gateway 401 proves only the first gate for a request without a token.

## 10. Evaluate the proposed metric contract on paper

Read [reporting contracts](reporting-contracts.md), then calculate the four-order example yourself:

1. Four distinct orders created: created = 4.
2. Two delivered and one final failed: completed = 3.
3. Success = `2 / 3 * 100 = 66.67%`; failure = `1 / 3 * 100 = 33.33%`.
4. The remaining active order moves from C1 to C2: C1 workload = 0, C2 = 1.
5. Redeliver the same events conceptually: all numbers stay unchanged.
6. Add another unchanged status event for the same order with a different event ID: it is still one order.
7. For no outcomes, both rates are null; counts are zero.
8. Convert `2026-11-07T18:30:00Z` to Colombo: midnight Nov 8. It belongs to Nov 8 even if received the next day.
9. Explain why cancellations and returns are outside the proposed success denominator, and why M6 must confirm final failure/retry semantics.
10. Explain why delivered does not mean on-time: no deadline comparison has been supplied.

**Pass criterion:** You and a teammate derive the same answer without inspecting implementation code. These are design acceptance examples, not event tests already executed.

## 11. Review handoffs with the owners

Open [handoff requests](handoff-requests.md). Confirm the owner, exact required interface, needed part, and pending decision for each row. Record actual agreement and its evidence before changing Pending to Confirmed.

Prioritize M9's queue/event guarantees for Part 2, M5/M6's reassignment/outcome rules for Part 3, and M1/M2's identity boundary for Part 4. M9's shared broker ownership is explicitly documented even though their service is notifications.

For the assistant example "How much to send 2 kg to Kandy?", explain why the missing origin needs clarification. Show M4's actual `EstimateRequest` fields; a city-only payload does not match the current API. No key/provider decision has been assumed.

## 12. Review and commit only your intended documents

```powershell
git diff --check
git status --short
```

You can split the documents into the roadmap's suggested small commits. Review each staged diff before committing:

```powershell
git add docs/design/m10/baseline-notes.md docs/test-reports/m10/part-1
git diff --cached --stat
git diff --cached
git commit -m "docs(m10): record analytics implementation baseline"

git add docs/design/m10/README.md docs/design/m10/reporting-contracts.md docs/design/m10/handoff-requests.md docs/design/m10/part-1-self-verification.md
git diff --cached
git commit -m "docs(m10): define report semantics and integration contracts"
```

The pre-existing roadmap may need a separate deliberate `git add docs/design/m10/implementation-roadmap.md` if you want it included. These commands are for your own use; no commits or pushes were performed by the agent.

Use the PR title `docs(m10): establish analytics baseline and contracts`, target `dev`, and link the evidence report. State known KPI/broker failures and pending handoffs. Do not describe Part 1 as fixing analytics or implementing the new API.

Stop each Maven server with Ctrl+C in its terminal when finished. If you no longer need the containers:

```powershell
docker compose stop analytics-postgres rabbitmq
# Stop redis too only if you started it for this check and no other work needs it.
```

Preserve development volumes. Do not use `down -v` for this verification.

## Viva explanations

| Question | Explanation you should be able to give |
|---|---|
| What did you deliver in Part 1? | A reproducible evidence baseline, precise report definitions, sample contracts, integration handoffs, and a verification guide before changing business behavior. |
| Why baseline first? | It distinguishes pre-existing defects from regressions and defines what correct output means before writing projections. |
| What is a projection? | An analytics-owned read model built from other services' events, without reading their databases. |
| Why two kinds of duplicate protection? | Event-ID uniqueness prevents a transport replay; per-order state prevents distinct repeated status events from counting the same business outcome twice. |
| Why one database transaction? | A processed marker without its metric update loses work; an update without its marker lets a retry double-count. Both must commit or roll back together. |
| Why event time and Colombo? | UTC preserves the actual instant; the explicit business timezone decides the reporting day consistently, even for delayed messages. |
| Why can tests pass while KPI GET fails? | Mock repositories don't enforce PostgreSQL transaction rules, and one existing test deliberately expects sample insertion. |
| Why can health say UP while messaging fails? | Health reports selected infrastructure checks; it does not necessarily prove that a listener declared its queue and consumed a message. |
| What is the success-rate denominator? | Delivered plus final failed outcomes in the selected period, under the proposed policy, not all orders created in that period. |
| Why null for an empty rate? | With no completed outcomes, the ratio is undefined. Zero counts are meaningful; 100% success would imply evidence that does not exist. |
| Why no SLA figure? | Delivery completion alone has no information about whether a promised deadline was met. |
| Why not trust callerPersona? | It is caller-controlled JSON. Authorization needs verified identity and backend roles from a trusted gateway boundary. |
| Which interfaces already exist? | Sanitized gateway tracking and district-based pricing estimates. The new chat route/reports are still proposals. |
| What remains after Part 1? | Owner confirmations, reliable event consumption, correct projections, authorized APIs, truthful AI tools/chat, and frontend implementation in later parts. |
