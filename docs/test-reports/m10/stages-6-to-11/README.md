# Stages 6–11: AI assistant verification

Executed on 2026-11-10 with Java 25, Maven 3.9.11, Windows PowerShell and Docker Desktop's
Linux engine. Stage numbers follow the Member 10 guide. See the
[connection guide](../../../design/m10/stages-6-to-11-ai-assistant.md) for reproducible setup.

## Results

### Verification after merging dev

Latest fetched `dev` (`bd0ded7`) was merged into the assistant branch before PR creation.
Conflicts were resolved by retaining the customer-only assistant contracts and consolidated
guides, while keeping dev's configured tracking client and contact-enrichment changes.
The analytics OpenAPI declaration was regenerated after resolving its specification.

Fresh merged-source checks passed: 103 analytics unit/controller tests, 26 PostgreSQL/RabbitMQ
integration tests, gateway verification, frontend API generation, lint and production build.
Shared contracts and affected services used temporary POM copies with only build output
changed to `target-pr-verify`, avoiding stale IDE-written record classes and running-JAR
locks. These temporary POMs were removed after verification. No paid provider was called;
live Azure acceptance remains pending university account access.

### Customer-only follow-up

The project owner clarified that only customers should see/use conversational chat.
The follow-up build passed 103 unit/controller/HTTP tests, including denial of SYSTEM_ADMIN,
DISPATCHER, MERCHANT and COURIER on all three chat routes, plus protected tracking ownership
and rejection before public tracking for foreign/forbidden orders. Frontend lint/build passed.
The customer UI reuses `AssistantLauncher` and `AssistantPanel` from the courier catalog.

Real requests through the running Vite proxy confirmed customer pricing, tracking an owned
order, identical answers for foreign and unknown orders, admin denial on all three chat routes,
customer audit denial and retained admin audit access. Demo customer: `m10.ai.customer`;
owned demo parcel: `SD-CXGJ-C72H`. Current analytics runs from `target-ai-customer` to avoid
replacing the previously running JAR. Earlier results below describe the preceding baseline.

| Check | Observed result |
|---|---|
| Original analytics baseline | Passed before code changes |
| Final analytics unit/controller suite | 100 tests, zero failures/errors/skips |
| PostgreSQL/RabbitMQ integration suite | 26 tests, zero failures/errors/skips |
| Gateway tests | 47 tests, zero failures/errors/skips |
| Isolated real-account assistant acceptance | 41 checks passed |
| Complete backend reactor `-DskipTests compile` | BUILD SUCCESS |
| `npm run api:sync:specs` | Passed; analytics types regenerated |
| Frontend lint and production build | Passed; existing bundle-size advisory remains |
| `docker compose config --quiet` | Passed |
| Live Azure model smoke | Not run: no permitted deployment/API credentials supplied |

The final suite includes the added real pricing HTTP timeout check. The full integration
verification ran after all application code changes; the final unit run then added timeout
test coverage without changing application behavior.

JaCoCo line coverage from the combined execution data: `analytics.ai` and subpackages 93.9%,
controllers 100%, services 93.0%. Coverage measures executed lines, not model quality or
production security guarantees.

## What the tests establish

- Mock tracking answers use returned API status, not fabricated snapshots.
- Mock pricing answers use the real API amount; missing origin prompts for clarification.
- Validated tools enforce tracking format, exact argument keys, known districts, numeric
  weights and inclusive 0.01–50 kg limits before issuing HTTP.
- Actual HTTP fixtures verify paths, pricing request shape, direct tracking response shape,
  authorization and correlation forwarding, safe field selection and bounded timeouts.
- Azure/OpenAI-compatible provider tests parse tool-call responses, send the deployment
  name and function schemas, submit tool outputs, and handle malformed/rate-limited responses.
  They use local HTTP fixtures and never contact a paid provider.
- Malicious tool requests cannot access unlisted tools. Private-data requests are refused.
- Tool execution stops at three calls; provider errors expose generic messages only.
- Foreign and expired conversation IDs cannot be read; limits are isolated by user.
- Legacy request/response adaptation ignores caller-supplied persona for authorization.
- Flyway fresh installs pass JPA validation. A V4-to-V5 upgrade removes historical raw
  audit text and accepts metadata-only audit records. Older V2/V3 upgrade scenarios also pass.
- Public gateway tracking cache misses resolve against the configured order-service URL.

## Real-account acceptance

The [acceptance script](../../../design/m10/fixtures/verify-ai-assistant.py) starts five
applications on random ports, creates disposable PostgreSQL databases, RabbitMQ and Redis,
registers three customer accounts, and logs in those accounts plus a bootstrapped admin.
It uses genuine IAM-issued access tokens rather than test-generated JWTs.

Its 41 successful checks include five application health checks, token/forged-header
rejection, real pricing comparison, origin clarification, conversation ownership, an unknown
parcel, real order creation, public tracking cache-miss fallback, tracking comparison, PII
exclusion, refusal behavior, legacy payload compatibility, customer/admin audit authorization,
safe persisted audits, rate limits and user isolation, aggregated OpenAPI, and an active
consumer on the real analytics RabbitMQ queue.

The script cleans up its own processes and containers. It does not drop normal development
volumes or access another service's database from application code. Test inspection uses
disposable storage only.

## Local artifacts and build isolation

Existing Java applications held the normal `target/*.jar` files open, and existing build
outputs contained stale record classes. Verification used temporary copies of each module
POM changing only the build output directory to `target-ai`. Source files, dependencies,
plugins, tests and Java settings were the same. These temporary POMs were removed afterward;
the normal build configuration is unchanged. Validation output folders are locally ignored.

Local evidence (not committed):

```text
ai-baseline-test.log
ai-stages-verify.log
ai-final-unit-test.log
ai-http-final-test.log
ai-gateway-test.log
ai-full-backend-compile.log
ai-acceptance.log
backend/analytics-ai-service/target-ai/surefire-reports/
backend/analytics-ai-service/target-ai/failsafe-reports/
backend/analytics-ai-service/target-ai/site/jacoco/index.html
backend/analytics-ai-service/target-ai/ai-acceptance/result.json
```

The reproducible guide uses the ordinary `target` directory. Stop older application JARs
before rebuilding with the normal commands.

## Remaining practical limits

- Stage 7's provider adapter is implemented and protocol-tested. Its live-provider acceptance
  criterion remains pending a successful Azure deployment, credentials and smoke request.
- Conversation storage and rate limits are per process. Use one instance for the demo;
  shared state and distributed limits are needed before enabling cross-pod continuity.
- Mock mode supports documented English questions and short district clarifications. It
  is not a general language model or a multilingual chat-quality evaluation.
- Prices are STANDARD actual-weight-only estimates. Dimensions/speed can change booking prices.
- No MCP server, dashboard charts or floating-widget wiring is included in Stages 6–11.
- Shared gateway/configuration changes need the respective owner's review. No PR, merge,
  deployment or external teammate message was performed.
