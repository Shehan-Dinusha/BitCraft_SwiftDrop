# Part 4 verification evidence

Implementation run: 2026-11-09, branch `Buddhika/feat/reporting-api`, Java 25, local Docker Desktop. This records checks actually executed, not a claim of team approval or merge.

| Check | Observed result |
|---|---|
| `mvn -f backend/pom.xml -pl analytics-ai-service -am verify` | BUILD SUCCESS; 61 analytics unit/controller/config cases, 25 PostgreSQL/RabbitMQ integration cases; zero failures, errors or skips. Common-contracts' separate test also passed. |
| Reporting controller suite | 18 cases cover both permitted roles, rejected roles, legacy aliases, missing identity, malformed/reversed/missing dates, the exact 90-day boundary, workload routing, null rates and safe 500 errors. |
| Running servlet HTTP and SQL | Exact daily totals/rates/workload agreed with real PostgreSQL; inclusive Colombo midnight boundaries and zero-filled dates verified. |
| Live `/v3/api-docs` | HTTP 200; new workload and existing KPI paths advertise bearer security. |
| Existing event/projection integration tests | All previous 23 cases retained and passed, including broker consumption, duplicate/rollback/DLQ, migration, reconciliation and weighted rates. |
| `npm.cmd run api:sync:specs` | Passed; analytics declaration regenerated from YAML. |
| `npm.cmd run lint` | Passed. |
| `npm.cmd run build` | Passed; existing bundle-size warning remains nonfatal. |
| Real IAM-token requests through gateway 8080 | Passed on 2026-11-10 using `docs/design/m10/fixtures/verify-stage5.py`: 30 checks with isolated IAM, gateway, analytics, PostgreSQL, RabbitMQ and Redis. Real admin/dispatcher/customer logins verified 200/403, missing-token 401, spoofed-header rejection, legacy protections, date validation and OpenAPI. Uses local HS256 configuration. |

Fresh reports are generated in `backend/analytics-ai-service/target/surefire-reports/` and `target/failsafe-reports/`; coverage is in `target/site/jacoco/`. These are build outputs and are not committed. Initial sandbox execution lacked Maven-cache/Docker permission; the successful run used approved access. An early run before adapting existing controller tests failed as expected when the new authorization rejected their missing test identities; the final run includes those adaptations and passes.

Manual evidence to add yourself: permitted-role 200 responses for all three reports, customer 403, no-token 401, forged identity-header rejection through gateway, invalid-range 400, matching demo SQL, and fixture replay/restart results. Never save tokens/passwords in submitted evidence.
