# Test results and coverage

Numbers from CI on commit `b6b2e76d`: [backend run 45047849399](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/runs/45047849399), [frontend run 45047849445](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/runs/45047849445). 22 November 2026.

## Backend

**1861 tests (1647 unit and slice, 214 integration), 0 failed, 0 skipped. Coverage 83.9% of lines, 68.9% of branches.**

| Module | Unit | Integration | Total | Failed | Skipped | Line coverage | Branch coverage |
|---|---|---|---|---|---|---|---|
| API Gateway | 103 | 0 | 103 | 0 | 0 | 82.7% (869/1051) | 67.2% (301/448) |
| Auth & IAM | 159 | 37 | 196 | 0 | 0 | 75.5% (1408/1864) | 59.3% (411/693) |
| Customer Order | 238 | 8 | 246 | 0 | 0 | 88.4% (1298/1469) | 70.6% (332/470) |
| Pricing & Zone | 58 | 3 | 61 | 0 | 0 | 93.2% (261/280) | 82.0% (41/50) |
| Dispatch | 240 | 68 | 308 | 0 | 0 | 88.3% (1308/1482) | 74.0% (427/577) |
| Delivery Execution | 165 | 26 | 191 | 0 | 0 | 88.8% (1273/1434) | 76.2% (487/639) |
| Merchant B2B | 92 | 0 | 92 | 0 | 0 | 77.7% (637/820) | 63.8% (183/287) |
| Webhook Resiliency | 186 | 41 | 227 | 0 | 0 | 87.3% (1390/1592) | 72.1% (544/754) |
| Notification | 221 | 3 | 224 | 0 | 0 | 86.1% (1485/1725) | 72.7% (835/1149) |
| Analytics & AI | 111 | 28 | 139 | 0 | 0 | 88.8% (984/1108) | 77.2% (433/561) |
| Common Contracts | 74 | 0 | 74 | 0 | 0 | 43.4% (170/392) | 28.2% (80/284) |
| **Total** | 1647 | 214 | **1861** | 0 | 0 | **83.9%** (11083/13217) | 68.9% (4074/5912) |

Integration tests (`*IT`) run against real PostgreSQL, RabbitMQ and Redis in Testcontainers. JaCoCo measures unit and
integration tests together. Common Contracts is mostly enums and records.

## Frontend

**179 tests in 36 files, 0 failed. Coverage 25.15% of lines, 25.67% of branches.** Files without any test count as 0%.

## Where each requirement is tested

| Requirement | Tests |
|---|---|
| Event and integration flow | `WebhookDeliveryFlowIT` (event → database → signed HTTP call → retry → replay), `OrderPersistenceAndEventIT`, `OrderCreatedConsumerIT`, `WorkloadReleaseIT`, `StatusEventPublishIT`, `BrokerNotificationIT`, `TelemetryConsumptionIT` |
| Duplicate events and dead letters | `WebhookIngestionIT`, `ConsumerFailureHandlingIT`, `DispatchDeadLetterTopologyIT`, `DeliveryDeadLetterTopologyIT` |
| A courier cannot reach another courier's delivery | `CourierHorizontalAuthTest`, `CourierReadAuthorizationTest`, `DeliveryAccessPolicyTest`, `CourierAccessAuthorizationTest` |
| Dispatchers stay in their district | `DispatchAuthorizationTest`, `HubLegTest`, `ReturnsIT`, `CashLedgerIT` |
| Customers and shops see only their own | `OrderOwnershipTest`, `MerchantOwnershipTest`, `PendingMerchantCannotOrderTest`, `WebhookSubscriptionServiceTest` |
| Roles and forged identity | `RbacIT`, `ApprovalRbacTest`, `JwtAuthenticationGlobalFilterTest`, `ApiKeyHeaderAuthTest` |

## Files here

- `jacoco-html.zip`: the JaCoCo pages of every module. Unzip and open `<module>/index.html`.
- `jacoco-csv/`: the coverage numbers per class.
- `test-results.zip`: the JUnit XML of every backend test class, unit and integration, and of the frontend.
- `frontend-coverage.json`: frontend coverage per file.

## Run them yourself

Docker must be running for the integration tests.

```bash
mvn -f backend/pom.xml verify          # reports in backend/<module>/target/site/jacoco/
cd frontend && npm ci && npm run test:coverage   # report in frontend/coverage/
```

CI runs both on every pull request. `jacoco:check` fails the build when a module falls below its floor.
