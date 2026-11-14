# Member 9 - Notification service test and coverage evidence

Execution date: 2026-11-14. Windows, Java 25, Maven 3.9.11, Docker Desktop (for
Testcontainers). Branch `Buddhika/feat/azure`, based on `dev` at `52e49ab`. These are
observed results, not a claim that the branch has been merged or accepted.

## Commands and results

| Check | Observed result |
|---|---|
| `mvn -f backend/pom.xml -pl notification-service -am test` before this change | BUILD SUCCESS, 75 tests (baseline) |
| `mvn -f backend/pom.xml -pl notification-service -am verify` after this change | BUILD SUCCESS. `common-contracts` 46/46, `notification-service` 87/87, zero failures, errors or skips |
| Whole module coverage | 73.3% line (595 of 812), 53.9% branch (244 of 453) |
| `service/` package | 90.6% line (221 of 244) |
| `service/email/` package | 83.3% line (60 of 72) |
| `controller/` package | 77.8% line (56 of 72) |
| `messaging/consumer/` package | 80.7% line (46 of 57) |

The 70% line-coverage target for `service/` and `controller/` is met.

## Required tests (member guide Part C)

| Test | Result | Proves |
|---|---|---|
| `BrokerNotificationIT` | Pass (Testcontainers RabbitMQ + Postgres) | A DELIVERED event published to `swiftdrop.events.exchange` produces a dispatch log and an in-app row |
| `OrderCreatedNotificationIT` | Pass | `order.created` produces the confirmation by SMS and EMAIL |
| `TemplatePiiTest` | Pass | Rendered messages never contain passwords, card numbers, full address or full phone |
| `MaskingTest` | Pass | Phone/email masking; dispatcher logs (including the mock email provider) never contain raw PII |
| `DuplicateEventTest` | Pass | The same `eventId` twice gives one notification |
| `DefinitionsJsonParityTest` | Pass | `definitions.json` holds the required queues and bindings |

## Tests added or changed by the Azure email work

| Test class | Cases | Proves |
|---|---|---|
| `AzureCommunicationEmailSenderTest` (new) | 5 | A local stub independently verifies the HMAC-SHA256 signature, content hash, date header and JSON body; the operation id is logged without the recipient; blank subjects get a default; HTTP 401 and an unreachable endpoint fail with safe messages that hold neither key nor recipient |
| `EmailSenderConfigTest` (new) | 5 | Mock is the default; Azure is selected only with valid settings; missing or malformed settings fail fast; a bad key is not echoed; unknown providers are rejected |
| `MultiChannelNotificationDispatcherTest` | +2 | EMAIL reaches the provider; a provider rejection is logged as FAILED with a safe reason; SMS never reaches the email provider |
| `NotificationEventConsumerTest` | updated | Out for delivery and failed dispatch SMS + EMAIL, but write exactly one in-app row |
| `StatusTriggersTest`, `OrderCreatedNotificationIT` | updated | Critical triggers build SMS then EMAIL requests |

## Live verification

See [Azure email notifications](../../design/m9/azure-email-notifications.md#verification-run-on-2026-11-14):
two orders booked through the gateway each delivered a real order-confirmation email
through Azure Communication Services, with one in-app row each.

## Files

- [surefire-reports/](surefire-reports/) - per-class test summaries
- [jacoco/index.html](jacoco/index.html) - coverage report
