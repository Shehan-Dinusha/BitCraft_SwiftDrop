# Member 9 - Notification service test and coverage evidence

Execution date: 2026-11-14. Windows, Java 25, Maven 3.9.11, Docker Desktop (for
Testcontainers). Branch `umesh/feat/azure-ai-email-notifications`, rebased on `dev` at `e3b6eec`.
These are observed results, not a claim that the branch has been merged or accepted.

Scope: real email through Azure Communication Services, branded HTML emails, and removal of the SMS channel.

## Commands and results

| Check | Observed result |
|---|---|
| `mvn -f backend/pom.xml -pl notification-service -am test` before these changes | BUILD SUCCESS, 75 tests (baseline) |
| `mvn -f backend/pom.xml -pl notification-service -am verify` after these changes | BUILD SUCCESS. `common-contracts` 46/46, `notification-service` 99/99, zero failures, errors or skips |
| `npm run lint` and `npm run build` in `frontend/` | Both pass |
| Whole module coverage | 79.2% line (675 of 852), 56.8% branch (272 of 479) |
| `service/` package | 91.8% line (223 of 243) |
| `service/email/` package | 89.2% line (99 of 111) |
| `controller/` package | 78.4% line (58 of 74) |
| `messaging/consumer/` package | 83.3% line (45 of 54) |

The 70% line-coverage target for `service/` and `controller/` is met.

## Required tests (member guide Part C)

| Test | Result | Proves |
|---|---|---|
| `BrokerNotificationIT` | Pass (Testcontainers RabbitMQ + Postgres, Flyway V1–V5) | A DELIVERED event published to `swiftdrop.events.exchange` produces a dispatch log and an in-app row |
| `OrderCreatedNotificationIT` | Pass | `order.created` produces exactly one EMAIL confirmation |
| `TemplatePiiTest` | Pass | Rendered messages never contain passwords, card numbers, full address or full phone |
| `MaskingTest` | Pass | Phone/email masking; a phone mistakenly used as an email destination is masked in error logs |
| `DuplicateEventTest` | Pass | The same `eventId` twice gives one notification |
| `DefinitionsJsonParityTest` | Pass | `definitions.json` holds the required queues and bindings |

## Tests added or changed

| Test class | Proves |
|---|---|
| `AzureCommunicationEmailSenderTest` (new, 5) | A local stub independently verifies the HMAC-SHA256 signature, content hash, date header and JSON body; the Azure operation id is logged without the recipient; blank subjects get a default; HTTP 401 and an unreachable endpoint fail with safe messages that hold neither key nor recipient |
| `EmailHtmlRendererTest` (new, 11) | Branded layout with headline, tracking box and button; one badge label and Velocity colour per status; all dynamic text HTML-escaped; unsafe or missing tracking numbers get no link; plain-text alternative carries the link |
| `EmailSenderConfigTest` (new, 5) | Mock is the default; Azure is selected only with valid settings; missing or malformed settings fail fast; a bad key is not echoed; unknown providers are rejected |
| `MultiChannelNotificationDispatcherTest` | EMAIL reaches the provider; disabled email is SUPPRESSED; missing or malformed addresses FAIL; provider rejection is logged safely; `SMS` is FAILED as "Unsupported channel"; PUSH never uses the email provider |
| `NotificationLogControllerTest` | `POST /logs/dispatch` with channel `SMS` returns 400 and never reaches the dispatcher |
| `PreferenceControllerTest`, `RecipientPreferenceServiceTest` | Preferences no longer contain `smsEnabled` or `phoneNumber` |
| `ContactClientTest` | IAM contact lookup resolves the email; a phone in the IAM response is ignored |
| `StatusTriggersTest`, `NotificationEventConsumerTest` | Every trigger builds one EMAIL request; one in-app row per recipient |

## Live verification

See [Azure email notifications](../../design/m9/azure-email-notifications.md#verification-run-on-2026-11-14).
After the SMS removal, the V5 migration applied on the running database, and an order booked
through the gateway for a Gmail customer produced exactly one `EMAIL SENT` row; Azure reported
the delivery operation as `Succeeded`.

## Files

- [surefire-reports/](surefire-reports/) - per-class test summaries
- [jacoco/index.html](jacoco/index.html) - coverage report
