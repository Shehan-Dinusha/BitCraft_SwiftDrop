# Member 10: Stages 1–5 completed

The numbering here follows `docs/member-guides/member-10.md`. These five stages correspond to the analytics backend work covered by roadmap Parts 1–4.

| Stage | What was built | Why it matters |
|---|---|---|
| 1. Idempotent consumer | Stable event-ID checks, transactionally stored inbox records, validation, retries and dead-letter handling. | Receiving the same delivery message twice does not count the parcel twice. |
| 2. Daily projections | Daily creation/delivery/failure tables and courier assignment/outcome/workload projections, including `order.created` consumption. | Converts delivery activity into useful statistics, handles reassignment and delayed events, and counts orders rather than status messages. |
| 3. Report endpoints | Daily delivery, success-rate and current workload APIs with inclusive Colombo dates and a maximum 90-day range. | A future dashboard can request real statistics for a chosen period. Empty reports have zero counts and undefined rates remain null. |
| 4. Aggregation tests | Counts, duplicates, weighted rates, date boundaries, empty results, real PostgreSQL/RabbitMQ and HTTP tests. | Checks that reported results agree with known facts and persisted data. |
| 5. Admin-only reports | Dispatcher/system-admin authorization using gateway identity, including legacy operational aliases. | Customers cannot read operational reports. Real IAM login tokens through gateway port 8080 now verify this. |

## Evidence

The previously recorded suite passed 61 analytics unit/controller/config tests and 25 database/broker integration tests. Frontend lint and build passed. On 2026-11-10, the isolated gateway acceptance script passed 30 checks with real IAM-created accounts:

- Admin and dispatcher: 200 for all three reporting routes.
- Customer: 403 for all three routes and checked legacy operational aliases.
- No token: 401 for all three routes.
- Customer token plus forged admin headers: still 403.
- Invalid, reversed, missing and overlong ranges: 400.
- Exactly 90 inclusive days: accepted with 90 zero-filled rows.
- Empty success rate: explicit null; aggregated OpenAPI: 200.

The gateway check uses the default local HS256 IAM configuration. It does not establish support for an RS256 deployment. Analytics continues to trust gateway headers; production downstream ports must stay internal. Cross-member policy/deployment reviews remain separate from this functional verification.

## Repeat the checks yourself

Follow the consolidated [verification guide](verification.md) for current Maven/frontend
checks, real-token gateway authorization and SQL reconciliation. The earlier evidence records
30 reporting gateway checks, but its historical `verify-stage5.py` helper is not present in
this checkout; do not treat it as a runnable command. Current reproducible AI/gateway acceptance
is provided by `fixtures/verify-ai-assistant.py`.

## Explain it in your viva

“My service receives delivery events reliably, converts them into daily delivery statistics and courier workload, and exposes the results through validated APIs. I prevent duplicate counts and handle late events and reassignment. Only dispatchers and system administrators can access operational reports. I verified both the calculations and real-account access through the gateway.”

Remaining work starts with the AI assistant stages: real tracking/pricing tools, mock and real model providers, chat orchestration and safeguards, dashboard charts, floating widget and final diagrams. This completion statement covers Stages 1–5, not the full Member 10 assignment.
