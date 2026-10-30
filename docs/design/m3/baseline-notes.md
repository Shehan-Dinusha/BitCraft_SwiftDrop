# M3 baseline notes: customer-order-service

Recorded before any PR 1 change, on branch `feature/m3-verify-and-secure` (from `dev` at `dbb82a0`).

## Environment

| Item | Value |
|---|---|
| JDK | Temurin 25.0.5.8 (selected through `JAVA_HOME`) |
| Maven | 3.9.15 |
| Docker | Desktop 28.5.1 (engine running) |
| Infra | `rabbitmq` and `order-postgres` from `docker compose` (both healthy) |
| Service | `mvn -f backend/pom.xml -pl customer-order-service spring-boot:run` on port 8082 |

Note: `spring-boot:run` with `-am` (as written in the member guide, A7) fails with "Unable to find a suitable main class" because the plugin also runs on the parent POM. Run it without `-am` after installing `common-contracts`.

## Build and tests

- `mvn -pl common-contracts -am -DskipTests install`: success.
- `mvn -pl customer-order-service -am test`: **BUILD SUCCESS**, 19 tests in the service, 0 failures, 0 errors.

| Test class | Tests |
|---|---|
| `OrderControllerTest` | 6 |
| `CustomerOrderServiceTest` | 7 |
| `OrderPdfGeneratorServiceTest` | 2 |
| `PricingCalculationServiceTest` | 2 |
| `TrackingNumberGeneratorTest` | 2 |

No Testcontainers, MockWebServer or JaCoCo is configured in the service POM yet.

## Smoke test (real service, real Postgres, real RabbitMQ)

Customer A = `11111111-...`, customer B = `22222222-...`, both sent as `X-User-Id` with `X-User-Role: CUSTOMER`. A temporary queue bound to `swiftdrop.events.exchange` with `order.#` captured events (deleted afterwards, as was the test row).

- `/actuator/health`: `UP`.
- `POST /api/v1/orders/estimate`: 200 with a fee breakdown.
- `POST /api/v1/orders` as A: 200, tracking number `SD-XXXX-XXXX`, row in `swiftdrop_order.customer_orders`.
- `order.created` reached the exchange with routing key `order.created`. The payload carries ids, districts and speed only, no names, addresses or phones.

## Endpoint table (HTTP status observed)

| Endpoint | As owner | As other customer (B) | No identity headers | Verdict |
|---|---|---|---|---|
| `GET /{orderId}` | 200 | **200** | **200** | No ownership check |
| `GET /tracking/{trackingNumber}` | n/a | **200** (full order) | n/a | No ownership check, returns the full order. Out of scope for PR 1, handle in Stage 9 |
| `GET /{orderId}/shipping-label` | n/a | **200** | **200** | No ownership check |
| `GET /{orderId}/delivery-receipt` | n/a | **200** | n/a | No ownership check |
| `GET /` (list) | works | empty list | n/a | Filtered by `X-User-Id`, but see below |
| `GET /?customerId=A` as B | n/a | **returns A's order** | n/a | The `customerId` query parameter overrides the header |
| `PUT /{orderId}` | n/a | **200** | n/a | No ownership check |
| `POST /{orderId}/cancel` | n/a | **200, order became CANCELLED** | n/a | No ownership check |
| `DELETE /{orderId}` | n/a | 409 (state rule, not an owner rule) | n/a | Probably blocked because the order was already cancelled by the previous probe (not isolated) |
| fee-revision accept / reject | not exercised | not exercised | n/a | Takes `X-User-Id`, check in Stage 3 |

## Observations

1. Customer B read, edited and cancelled customer A's order. Stages 2 and 3 are real security fixes, not hygiene.
2. The role header (`X-User-Role`) is never read by the service. `JwtHeaderConstants.X_USER_ROLE` exists in `common-contracts`.
3. Logging: no personal data appeared in the service log for this run (searched for the street, contact name and phone). Still to review for Stage 5: `GlobalExceptionHandler` logs the validation `errors` map and `ex.getMessage()` for illegal-argument errors.
4. `CreateOrderRequest` is a flat Lombok class using only `@NotBlank`/`@NotNull`, no length, character or phone-format rules (Stage 4). `AGENTS.md` asks for `record` DTOs; the existing DTOs are Lombok classes, so converting them is out of scope for PR 1.
5. The request still carries `codAmount` and `declaredValue`. The project rule is cash at pickup only, so these need a decision from the team (not changed in PR 1).
6. Pricing is a local copy of the pricing logic (`PRICING-2026-V1`). M4's controller exposes `POST /api/v1/pricing/quote`, not `/estimate` as the guide says. Raise with M4 before PR 2.
7. `RabbitMQConfig` declares only `customer-order.fee-revision.queue`. Check this before Stage 12.
