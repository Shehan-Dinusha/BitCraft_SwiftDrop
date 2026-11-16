# Member 9 - Notification service test and coverage evidence

Execution date: 2026-11-16. Windows, Java 25, Maven 3.9.11, Docker Desktop 29.4.3 (for
Testcontainers). Branch `umesh/fix/m9-notification-review`, based on `dev` at `a7a22a9`.
These are observed results, not a claim that the branch has been merged or accepted.

Scope: review of the whole Member 9 area after PR #72 (Azure email) and PR #78 (OTP email),
with fixes for access control, integration-test execution, broker definitions and documentation.

## Correction to earlier reports

Earlier versions of this report and the PR #72 description listed `BrokerNotificationIT` as
passing. That was wrong: neither `*IT` class ran in `mvn verify`, because Surefire only picks up
`*Test` classes and Failsafe was not enabled in `notification-service/pom.xml`. The passing result
shown was a stale report file from 2026-11-10. This branch enables Failsafe, and both integration
tests now run and pass in a normal `mvn verify` (results below).

## Commands and results

| Check | Observed result |
|---|---|
| `mvn -f backend/pom.xml -pl notification-service -am verify` on `dev` before this branch | BUILD SUCCESS, 110 unit tests; **0 integration tests ran** |
| `mvn -f backend/pom.xml -pl notification-service -am verify` on this branch | BUILD SUCCESS. `common-contracts` 46/46, notification unit tests **124/124**, integration tests **2/2** (Failsafe) |
| `BrokerNotificationIT` (Testcontainers RabbitMQ + Postgres, Flyway V1–V6) | Pass, 119.4 s |
| `OrderCreatedNotificationIT` | Pass |
| `npm run lint` and `npm run build` in `frontend/` | Both pass |
| Whole module coverage | 86.5% line (778 of 899), 66.6% branch (361 of 542) |
| `service/` package | 89.2% line (224 of 251) |
| `service/email/` package | 89.6% line (103 of 115) |
| `controller/` package | 93.3% line (83 of 89) |
| `messaging/consumer/` package | 92.9% line (52 of 56) |
| `messaging/trigger/` package | 86.6% line (149 of 172) |

The 70% line-coverage target for `service/` and `controller/` is met.

## Required tests (member guide Part C)

| Test | Result | Proves |
|---|---|---|
| `BrokerNotificationIT` | Pass | A DELIVERED event published to `swiftdrop.events.exchange` produces a dispatch log and an in-app row |
| `OrderCreatedNotificationIT` | Pass | `order.created` produces exactly one EMAIL confirmation |
| `TemplatePiiTest` | Pass | Rendered messages never contain passwords, card numbers, full address or full phone |
| `MaskingTest` | Pass | Phone/email masking; dispatcher logs never contain raw PII |
| `DuplicateEventTest` | Pass | The same `eventId` twice gives one notification |
| `DefinitionsJsonParityTest` | Pass | `definitions.json` holds the required queues and bindings, and declares each binding once |

## Tests added on this branch

| Test class | Cases | Proves |
|---|---|---|
| `NotificationLogControllerTest` | +7 | Missing identity → 401; a customer sees only their own logs and gets 403 for another user's logs or log detail; DISPATCHER/SYSTEM_ADMIN list every recipient (operations console); a customer's self-test ignores the requested destination; a customer cannot dispatch to another user; nobody can dispatch an OTP template manually |
| `PreferenceControllerTest` | +4 | Missing identity → 401; reading another user's preferences → 403 (DISPATCHER allowed); `GET /preferences` is scoped to the caller; only the user or SYSTEM_ADMIN can change preferences |
| `OtpEmailDispatchTest` | +2 | A one-time code goes only to the registered IAM address, never to a preference or requested address; without an IAM address nothing is sent |
| `DefinitionsJsonParityTest` | +1 | No duplicate bindings in `definitions.json` |

## Files

- [surefire-reports/](surefire-reports/) - unit test summaries
- [failsafe-reports/](failsafe-reports/) - integration test summaries
- [jacoco/index.html](jacoco/index.html) - coverage report
