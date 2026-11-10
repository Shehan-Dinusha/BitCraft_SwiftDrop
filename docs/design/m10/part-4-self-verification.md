# Part 4: Your testing, commit and demonstration guide

Run these checks yourself. The [implementation explanation](part-4-reporting-api.md) covers each changed area, viva answers and a team update. Expected results below are acceptance targets; do not describe a manual step as passed until you observe it.

## 1. Check your branch and automated suite

Start Docker Desktop. From the repository root in PowerShell:

```powershell
git branch --show-current
git status --short
java -version
mvn -version
docker version
mvn -f backend/pom.xml -pl analytics-ai-service -am clean verify
```

The implementation was made on `Buddhika/feat/reporting-api`; do not switch branches with uncommitted changes. Expect BUILD SUCCESS, 61 analytics unit/controller/config cases, 25 integration cases and the shared-contract module's separate test, with no failures/errors/skips. `verify` runs Failsafe; `test` alone does not execute integration tests. Expected deliberate failure/retry logs in broker tests are not test failures.

```powershell
Get-Content backend/analytics-ai-service/target/surefire-reports/com.swiftdrop.analytics.controller.AnalyticsReportControllerTest.txt
Get-Content backend/analytics-ai-service/target/failsafe-reports/com.swiftdrop.analytics.TelemetryConsumptionIT.txt
Get-Content backend/analytics-ai-service/target/failsafe-reports/failsafe-summary.xml
Push-Location frontend
npm.cmd run api:sync:specs
npm.cmd run lint
npm.cmd run build
Pop-Location
```

All commands should succeed. The existing >500 kB frontend bundle warning is nonfatal. These tests use disposable containers, not your ordinary database. The two new HTTP integration tests are `reportingHttpMatchesSqlWithInclusiveBoundariesAndZeroFilledDates` and `emptyReportingHttpAndLiveOpenApiMatchPublicContract`.

## 2. Start an isolated analytics demo

Use the same isolated database/vhost from the Part 3 guide so its fixture publisher works. If you already created them, reuse them; do not rerun creation commands expecting them to overwrite anything.

```powershell
docker compose up -d rabbitmq analytics-postgres redis iam-postgres
docker compose ps rabbitmq analytics-postgres redis iam-postgres
```

Only if the demo database/vhost do not exist:

```powershell
docker compose exec -T analytics-postgres psql -U swiftdrop -d postgres -c "CREATE DATABASE swiftdrop_analytics_part3_demo OWNER swiftdrop;"
docker compose exec -T rabbitmq rabbitmqctl add_vhost m10-part3-demo
docker compose exec -T rabbitmq rabbitmqctl set_permissions -p m10-part3-demo swiftdrop '.*' '.*' '.*'
```

In the **analytics terminal**, start the service using the demo overrides:

```powershell
mvn -f backend/pom.xml -pl common-contracts -am -DskipTests install
$env:DB_URL = 'jdbc:postgresql://localhost:5440/swiftdrop_analytics_part3_demo'
$env:SPRING_RABBITMQ_VIRTUAL_HOST = 'm10-part3-demo'
mvn -f backend/analytics-ai-service/pom.xml spring-boot:run
```

Expect migrations through V4 and successful consumer startup. Part 4 adds no V5. Existing credentials come from application configuration/environment; use your configured values if they differ.

In a separate **IAM terminal**, use the existing local IAM startup process:

```powershell
mvn -f backend/auth-iam-service/pom.xml spring-boot:run
```

Use existing admin/dispatcher/customer accounts. If you have no administrator, the IAM service supports `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD`; set them only in that IAM terminal according to `.env.example` and choose a local password. Then use the existing user-management/seed workflow to create staff accounts. Public registration cannot grant dispatcher/admin roles. The existing frontend seed script documents `SEED_ADMIN_USER`/`SEED_ADMIN_PASSWORD` if needed. Do not use a UI development persona as proof of identity.

In a separate **gateway terminal**:

```powershell
mvn -f backend/api-gateway/pom.xml spring-boot:run
```

Do not copy analytics' demo `DB_URL` or RabbitMQ virtual-host overrides into IAM/gateway terminals. Gateway uses its ordinary broker vhost; only analytics consumes the isolated fixture vhost. Default gateway analytics URL is `http://localhost:8089`. Ensure it is not overridden to another instance.

In a **control terminal**:

```powershell
curl.exe -i http://localhost:8089/actuator/health
curl.exe -i http://localhost:8081/actuator/health
curl.exe -i http://localhost:8080/actuator/health
curl.exe -i http://localhost:8080/api/v1/analytics/v3/api-docs
```

Expect healthy services and 200 OpenAPI. If a gateway health dependency is down, inspect logs and its Redis/broker connection before continuing. Stop any older analytics process using 8089 before launching the new code.

## 3. Obtain real tokens through the gateway

Use your own account names/passwords. This helper prompts without hardcoding passwords in a script:

```powershell
function Get-DemoToken([string]$Username) {
    $credential = Get-Credential -UserName $Username -Message 'Local SwiftDrop login'
    $body = @{
        username = $credential.UserName
        password = $credential.GetNetworkCredential().Password
    } | ConvertTo-Json
    $login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/login' -ContentType 'application/json' -Body $body
    return $login.accessToken
}
$adminToken = Get-DemoToken 'demo.admin'
$dispatcherToken = Get-DemoToken 'demo.dispatcher'
$customerToken = Get-DemoToken 'demo.customer'
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
$dispatcherHeaders = @{ Authorization = "Bearer $dispatcherToken" }
$customerHeaders = @{ Authorization = "Bearer $customerToken" }
```

Substitute existing account names. IAM login returns `accessToken` at the top level. Keep tokens local, out of screenshots/commits. Refresh them by logging in again if expired.

## 4. Test an empty range and authentication first

Choose a date absent from your demo database:

```powershell
$emptyDate = '2000-01-01'
$base = 'http://localhost:8080/api/v1/analytics'
Invoke-RestMethod "$base/deliveries/daily?from=$emptyDate&to=$emptyDate" -Headers $adminHeaders | ConvertTo-Json -Depth 8
Invoke-RestMethod "$base/success-rate?from=$emptyDate&to=$emptyDate" -Headers $dispatcherHeaders | ConvertTo-Json -Depth 8
```

Expect 200: one zero-filled day; zero completed/delivered/failed and explicit null success/failure percentages. On a fresh demo database, workload should have zero total and `couriers: []`. On a reused database it reflects all current workload, not the empty date filter.

```powershell
curl.exe -i "$base/couriers/workload"
curl.exe -i -H "Authorization: Bearer $customerToken" "$base/couriers/workload"
curl.exe -i -H "Authorization: Bearer invalid-token" "$base/couriers/workload"
```

Expect 401 without a token, 403 for customer, 401 for invalid token.

## 5. Publish known events and compare reports with SQL

In the control terminal, reuse the explicit synthetic Part 3 fixture:

```powershell
& ./docs/design/m10/fixtures/publish-part3-demo.ps1 -ReportDate '2026-11-08'
$from = '2026-11-08'
$to = '2026-11-08'
Invoke-RestMethod "$base/deliveries/daily?from=$from&to=$to" -Headers $adminHeaders | ConvertTo-Json -Depth 8
Invoke-RestMethod "$base/success-rate?from=$from&to=$to" -Headers $dispatcherHeaders | ConvertTo-Json -Depth 8
Invoke-RestMethod "$base/couriers/workload" -Headers $adminHeaders | ConvertTo-Json -Depth 8
```

The publisher prompts for broker credentials and targets the isolated Part 3 vhost. If scripts are blocked, use the process-only invocation documented in Part 3. For a fresh fixture expect **created 4, delivered 2, failed 1, completed 3, success 66.67%, failure 33.33%, active 1**. C1 has zero workload, C2 one. Older fixtures in a reused database can add workload; inspect all assignment dates when reconciling that total.

Open pgAdmin at analytics port 5440, database `swiftdrop_analytics_part3_demo`, and run:

```sql
SELECT report_date, created, delivered, failed
FROM daily_delivery_stats
WHERE report_date BETWEEN DATE '2026-11-08' AND DATE '2026-11-08'
ORDER BY report_date;

SELECT sum(delivered) delivered, sum(failed) failed,
       sum(delivered + failed) completed,
       round(100.0 * sum(delivered) / nullif(sum(delivered + failed), 0), 2) success_rate_percent
FROM daily_delivery_stats
WHERE report_date BETWEEN DATE '2026-11-08' AND DATE '2026-11-08';

SELECT c.courier_id, coalesce(sum(d.active), 0) active_orders
FROM analytics_couriers c LEFT JOIN courier_daily_stats d USING(courier_id)
GROUP BY c.courier_id ORDER BY c.courier_id;

SELECT order_id, created_at, outcome, outcome_at, courier_id, conflict_reason
FROM analytics_order_projection ORDER BY order_id;
```

SQL and API must agree. Check RabbitMQ Management at `http://localhost:15672`, vhost `m10-part3-demo`: main queue drains, valid fixture events do not accumulate in the DLQ. Replay the same fixture; counts must remain unchanged. Restart analytics and repeat; persisted totals remain unchanged.

## 6. Check validation and inclusive dates

```powershell
curl.exe -i -H "Authorization: Bearer $adminToken" "$base/deliveries/daily"
curl.exe -i -H "Authorization: Bearer $adminToken" "$base/deliveries/daily?from=bad&to=2026-11-08"
curl.exe -i -H "Authorization: Bearer $adminToken" "$base/success-rate?from=2026-11-09&to=2026-11-08"
curl.exe -i -H "Authorization: Bearer $adminToken" "$base/deliveries/daily?from=2026-01-01&to=2026-04-01"
curl.exe -i -H "Authorization: Bearer $adminToken" "$base/deliveries/daily?from=2026-01-01&to=2026-03-31"
```

Expect four 400 responses followed by 200 with 90 rows. Both endpoints use the same date validator. Request Nov 8–Nov 9 around the fixture and check Nov 8 is included and Nov 9 is zero if it contains no other events. The HTTP integration test additionally checks `2026-11-07T18:29:59Z` is excluded from Nov 8 and `18:30:00Z` is included; it checks the last second of Nov 8 and the start of Nov 9 too. This proves Colombo boundaries, not merely default machine timezone behavior.

## 7. Prove roles and legacy aliases cannot bypass access

```powershell
curl.exe -i -H "Authorization: Bearer $customerToken" "$base/kpis"
curl.exe -i -H "Authorization: Bearer $customerToken" 'http://localhost:8080/api/v1/operational-kpi/couriers'
curl.exe -i -H "Authorization: Bearer $customerToken" "$base/ai/audits"
curl.exe -i -H "Authorization: Bearer $customerToken" 'http://localhost:8080/api/v1/ai-delivery-assistant/audits'
curl.exe -i -X POST -H "Authorization: Bearer $customerToken" "$base/snapshots/aggregate"
curl.exe -i -H "Authorization: Bearer $customerToken" -H 'X-User-Role: SYSTEM_ADMIN' -H 'X-User-Id: 00000000-0000-0000-0000-000000000001' "$base/couriers/workload"
```

All must return 403. The last request proves the gateway removes forged role/user headers and forwards the customer's token identity. Repeat the three new report calls with the dispatcher token and then admin token; all valid requests must return 200. Sending JSON `callerPersona: SYSTEM_ADMIN` must never grant customer access to protected POST operations; the automated suite verifies this before body binding.

Diagnostic direct-port requests without gateway identity headers now return 401 for protected routes. Supplying fake headers to 8089 is **not a legitimate end-to-end authorization test**: downstream headers are trusted only inside the deployment boundary. Report any external downstream exposure to M1/M2.

## 8. Inspect OpenAPI and retain evidence

Open `http://localhost:8080/swagger-ui.html`, choose analytics, and authorize with an admin/dispatcher bearer token. Inspect all three routes, required dates, the 90-day rule and bearer requirement. Check nullable rate schemas and use Try it out. If stale docs appear, verify gateway points to the new process and refresh.

Keep screenshots of successful reports, matching SQL, 400/401/403 responses and test summaries. Do not include passwords/tokens. Record your actual manual outcome under `docs/test-reports/m10/part-4/`; the implementation evidence file explicitly distinguishes automated checks from the pending real-account demonstration.

## 9. Commit the changes yourself

No commits or pushes were made by the agent. Your current branch already exists; review it before staging. Ensure Part 3 is merged into `dev` or explicitly identify it as a PR dependency. Do not switch away from this uncommitted branch.

Keep runtime behavior and its tests together:

```powershell
git status --short
git diff --check
git add backend/analytics-ai-service/src/main backend/analytics-ai-service/src/test
git add docs/api-specs/analytics-ai-service.yaml frontend/src/types/generated/analytics-ai-service.ts
git diff --cached
git commit -m "feat(analytics): add authorized date-filtered reporting APIs"
```

Then commit the explanation and evidence:

```powershell
git add docs/design/m10/README.md docs/design/m10/implementation-roadmap.md
git add docs/design/m10/reporting-contracts.md docs/design/m10/handoff-requests.md
git add docs/design/m10/part-4-reporting-api.md docs/design/m10/part-4-self-verification.md
git add docs/test-reports/m10/part-4/README.md
git diff --cached
git commit -m "docs(m10): document reporting authorization and verification"
git status
git push -u origin HEAD
```

If unrelated changes exist, stage exact files/selected ranges instead of whole analytics source folders. Do not stage `.env`, credentials, `target/`, verbose temporary logs, or unrelated generated files. Type regeneration may normalize line endings in other declarations without content changes; those do not belong in this PR.

Open a PR into `dev` titled **feat(analytics): add authorized date-filtered reports**. Mention the three routes, 90-day inclusive validation, null empty rates, new protection on operational aliases and safe 500 behavior. Report actual tests; mark any unperformed gateway checks as pending. Request M1/M2 review of the trust boundary and relevant frontend review of stricter operational access. After `git fetch origin`, inspect `git diff --stat origin/dev...HEAD` for dependency/unrelated changes. Do not claim the part merged until it actually is.

Close demo application terminals with Ctrl+C. Close the terminal containing demo environment overrides, or remove those variables before normal development. Preserve database volumes; cleanup does not require `docker compose down -v`.
