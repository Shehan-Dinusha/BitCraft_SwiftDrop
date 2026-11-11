# Member 10 verification

This consolidates the former Part 1–4 self-verification guides. The member guide defines
the scope; the architecture documents explain the implementation; test reports record
what was actually executed. Keep historical evidence separate from current acceptance.

## Automated checks

Use Java 25, Maven 3.9+, Node.js, Python 3 and Docker Desktop's Linux engine. Run from
the repository root with model mode set to mock. Close old JAR processes before packaging
on Windows; a running JAR cannot be replaced. Avoid IDE builds writing into Maven output.

```powershell
$env:AI_PROVIDER = 'mock'
$env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
mvn -f backend/pom.xml -pl analytics-ai-service -am verify
mvn -f backend/pom.xml -pl api-gateway -am test
mvn -f backend/pom.xml -DskipTests compile

Push-Location frontend
npm.cmd run api:sync:specs
npm.cmd run lint
npm.cmd run build
Pop-Location
docker compose config --quiet
```

Check each command's exit code. Surefire covers unit/controller tests; Failsafe runs
`TelemetryConsumptionIT` against disposable PostgreSQL and RabbitMQ. Inspect `target/surefire-reports`,
`target/failsafe-reports` and `target/site/jacoco`. An actual Docker failure is not a skipped
or successful integration test. Logs/build artifacts remain local and ignored.

## What backend verification proves

- The stable event ID is registered with `INSERT ... ON CONFLICT DO NOTHING` in the
  same transaction as projection updates. Redelivery is harmless; a failed projection
  rolls back both effects. Database uniqueness also protects concurrent service instances.
- Valid events commit before acknowledgement. Invalid messages and exhausted bounded
  retries go to the analytics DLQ. Java queue declarations match broker definitions.
- Delayed events, reassignment and retries are reduced into distinct-order projections;
  restart/catch-up does not double-count. Reporting uses occurrence dates in Asia/Colombo.
- Counts and weighted outcome rates match persisted facts. Current workload sums active
  contributions across dates; it is not a historical end-of-day snapshot or shift availability.
- Fresh databases and V2/V3 upgrades work. V5 removes historical raw AI audit text and
  accepts safe metadata-only audit rows.
- Chat validates tool names/arguments, never fabricates downstream results, checks customer
  order ownership and stops after three tool calls. Non-customer roles cannot use any chat alias.

For exact Java classes and accepted policy, read [event reliability](part-2-event-reliability.md),
[projections](part-3-analytics-projections.md), [reporting APIs](part-4-reporting-api.md) and
[report meanings](reporting-contracts.md).

## Real-token gateway and assistant acceptance

```powershell
mvn -f backend/pom.xml -pl auth-iam-service,api-gateway,analytics-ai-service,pricing-zone-service,customer-order-service -am -DskipTests package
python docs/design/m10/fixtures/verify-ai-assistant.py
```

The script starts disposable infrastructure and five applications on random ports. It uses
real IAM registrations/logins, checks tracking/pricing against source APIs, customer-only
chat and cross-customer isolation, safe audit access, rate limits, OpenAPI and RabbitMQ.
It cleans up only its own processes/containers and makes no paid model calls. Results are
under `backend/analytics-ai-service/target/ai-acceptance/`.

For manual report checks, obtain real DISPATCHER/SYSTEM_ADMIN/CUSTOMER IAM access tokens.
IAM `/api/v1/auth/login` returns `accessToken` at the top level. Send tokens through gateway
8080, never fabricated identity headers or frontend development personas.

| Request | Expected |
|---|---|
| Daily/success-rate/workload reports as dispatcher/admin | 200 |
| Reports or audit-reading as customer | 403 |
| Protected requests without a token | 401 |
| Report request with customer token plus forged admin headers | 403 |
| Chat as customer with owned parcel | Public-safe status from the real service |
| Chat for foreign/unknown parcels | Same account-safe unavailable response |
| Chat as admin/dispatcher/courier/merchant, including legacy aliases | 403 |
| Dates missing, malformed, reversed or over 90 inclusive days | 400 |
| Exactly 90 inclusive dates | 90 daily rows, zero-filled when absent |
| Success-rate range with no outcomes | Zero counts and null percentages |

Report paths are `/api/v1/analytics/deliveries/daily?from=...&to=...`,
`/api/v1/analytics/success-rate?from=...&to=...` and `/api/v1/analytics/couriers/workload`.
Workload has no date filter. Gateway OpenAPI is `/api/v1/analytics/v3/api-docs`.

## Controlled projection demonstration

The integration suite already isolates and verifies the four-order dataset: created 4,
delivered 2, failed 1, completed 3, success 66.67%, failure 33.33%, C1 active 0 and C2 active 1.
The existing `fixtures/publish-part3-demo.ps1` is for an explicitly configured disposable
analytics database and `m10-part3-*` RabbitMQ virtual host. Do not publish it into normal
development storage or delete shared queues/volumes to obtain empty results.

In that disposable analytics database, compare report responses with:

```sql
SELECT report_date, created, delivered, failed
FROM daily_delivery_stats ORDER BY report_date;

SELECT sum(delivered) delivered, sum(failed) failed,
       round(100.0 * sum(delivered) / nullif(sum(delivered + failed), 0), 2) success_rate_percent
FROM daily_delivery_stats;

SELECT c.courier_id, coalesce(sum(d.active), 0) active_orders
FROM analytics_couriers c LEFT JOIN courier_daily_stats d USING(courier_id)
GROUP BY c.courier_id ORDER BY c.courier_id;

SELECT count(*) accepted_events,
       count(*) FILTER (WHERE NOT projection_applied) pending_projection
FROM processed_telemetry_events;
```

Replay identical events and restart analytics: totals must remain unchanged. Check the
disposable RabbitMQ main queue drains and valid events do not enter its DLQ. A queue-type
mismatch requires an agreed rollout or separate demo virtual host; never delete shared
queues as a workaround.

## Review and evidence

Use `git diff --check` and review exact staged paths. Exclude `.env`, model keys, JWTs,
personal data and generated build output. Keep curated results under `docs/test-reports/m10`.
Do not claim Azure success until a permitted real deployment has returned a verified tool-backed
answer. See the [AI setup guide](stages-6-to-11-ai-assistant.md) for browser tests and Azure settings.

The viva distinction is: events provide reliable facts, projections provide reports, and
the assistant interprets customer questions while tools retrieve authorized source data.
The model does not calculate authoritative prices or read another service's database.
