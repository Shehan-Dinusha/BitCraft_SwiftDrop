# Backend Agent Instructions

These rules apply to all work below [backend](.).

## Inspect first

Read the owning module’s `pom.xml`, `application.yml`, `application-aks.yml`, controller/service/entity/repository classes, and Flyway scripts before editing. For shared behavior, inspect [common-contracts](common-contracts) first. For gateway work, inspect [GatewayRoutesConfig.java](api-gateway/src/main/java/com/swiftdrop/gateway/config/GatewayRoutesConfig.java), [JwtAuthenticationGlobalFilter.java](api-gateway/src/main/java/com/swiftdrop/gateway/filter/JwtAuthenticationGlobalFilter.java), and the tracking hub package.

## Package and ownership conventions

Use the existing package layout:

```text
config/
controller/
service/
domain/entity/
domain/repository/
messaging/publisher/
messaging/consumer/
dto/
exception/
```

`delivery-execution-service` owns the delivery FSM, state guards, courier transitions, PoD storage/verification, and publication of `DeliveryStateChangedEvent` and `LocationUpdatedEvent`. `api-gateway` owns the tracking consumer, public tracking DTO, PII sanitizer proxy, and SSE/WebSocket broadcast hub. Do not move tracking handlers back into delivery execution.

## REST and OpenAPI contract rules

- Controllers accept and return Java 25 `record` DTOs, never JPA entities directly.
- Use `ApiResponse<T>` for standard service responses where the existing endpoint contract uses the shared envelope.
- Add `@Tag` at controller level, `@Operation` to each endpoint, and `@Schema` to public DTOs and fields.
- Use Jakarta validation annotations such as `@NotNull`, `@NotBlank`, and `@Valid` at the request boundary.
- Keep gateway aggregation URLs aligned with `/api/v1/<service>/v3/api-docs`; the gateway’s own tracking document is `/v3/api-docs`.
- After public contract changes, regenerate frontend declarations with `npm run api:sync:specs` or `npm run api:sync:local`.

## RabbitMQ conventions

- Use the durable topic exchange `swiftdrop.events.exchange` for the delivery tracking flow.
- Delivery state and location events use `parcel.status.changed`; the gateway queue binds with `parcel.status.changed.#`.
- Preserve `eventId`, `correlationId`, and event timestamps. Use [JwtHeaderConstants.java](common-contracts/src/main/java/com/swiftdrop/contracts/security/JwtHeaderConstants.java) and `X-Correlation-Id` for HTTP propagation.
- Consumers must be idempotent: tolerate duplicate delivery, use event IDs where persistence or side effects are added, and do not leak raw event PII to public clients.
- Keep broker declarations synchronized between Java `RabbitMQConfig.java` and [definitions.json](../infra/local/rabbitmq/definitions.json).

## Database and Flyway

- Every business service owns its own database and migration history. Cross-service foreign keys and shared database tables are forbidden.
- Add migrations as new versioned files: `V<N>__<description>.sql`.
- Never rewrite an already-applied migration. Verify the entity, repository, migration, local JDBC URL, and AKS JDBC URL together.
- Do not introduce a database dependency into `api-gateway`.

## Validation

```powershell
mvn -f backend/pom.xml -pl <module> -am -DskipTests compile
mvn -f backend/pom.xml -DskipTests compile
```

For event or schema changes, also validate RabbitMQ JSON, run the relevant service tests, and regenerate API types.
