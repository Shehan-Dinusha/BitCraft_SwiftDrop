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

`delivery-execution-service` owns the delivery FSM, state guards, courier transitions, PoD storage/verification, and publication of `DeliveryStateChangedEvent`. SwiftDrop does not use device location; do not add GPS endpoints, columns or events. `api-gateway` owns the tracking consumer, public tracking DTO, PII sanitizer proxy, and SSE/WebSocket broadcast hub. Do not move tracking handlers back into delivery execution.

## REST and OpenAPI contract rules

- Controllers accept and return Java 25 `record` DTOs, never JPA entities directly.
- Use `ApiResponse<T>` for standard service responses where the existing endpoint contract uses the shared envelope.
- Add `@Tag` at controller level, `@Operation` to each endpoint, and `@Schema` to public DTOs and fields.
- Use Jakarta validation annotations such as `@NotNull`, `@NotBlank`, and `@Valid` at the request boundary.
- Keep gateway aggregation URLs aligned with `/api/v1/<service>/v3/api-docs`; the gateway’s own tracking document is `/v3/api-docs`.
- After public contract changes, regenerate frontend declarations with `npm run api:sync:specs` or `npm run api:sync:local`.
- Every public path needs a matching gateway route in `GatewayRoutesConfig.java`; a controller path the gateway does not route is unreachable. Do not add path aliases (a second path for the same endpoint). If an old alias must keep working, hide it from the docs with `springdoc.paths-to-exclude`.

## OpenAPI / Swagger conventions

- Each service's `config/OpenApiConfig.java` sets the server to `"/"`, so "Try it out" calls whichever host served Swagger UI (local gateway or AKS ingress). Never hard-code `http://localhost:8080`.
- JWT is the default: `OpenApiConfig` adds a global `bearerAuth` security requirement (auth-iam is the exception: it marks its protected operations individually because most of its endpoints are public). Operations authenticated differently override it, for example `@SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME)` on the merchant B2B endpoints.
- Service-to-service endpoints live under `/internal/**`. The gateway never routes them, and `springdoc.paths-to-exclude: /internal/**` keeps them out of the public docs.
- The checked-in specs in `docs/api-specs/*.yaml` are curated by hand, and the frontend's generated type names come from them. Edit them surgically to match the code. Do not overwrite them with a raw springdoc export, which renames schemas and breaks frontend imports.

## Error handling

- Each service's `exception/GlobalExceptionHandler` may keep a catch-all `@ExceptionHandler(Exception.class)`, but it must first return Spring MVC's own client errors with their real status: `if (ex instanceof ErrorResponse e && e.getStatusCode().is4xxClientError())`. Otherwise unknown paths, wrong methods and missing parameters become 500s.
- A 500 response never contains `ex.getMessage()`, because database and library messages can carry personal data. Log the details and return a generic message.

## RabbitMQ conventions

- Use the durable topic exchange `swiftdrop.events.exchange` for the delivery tracking flow.
- Delivery state events use `parcel.status.changed`; the gateway queue binds with `parcel.status.changed.#`.
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

```powershell
# Unit tests
mvn -f backend/pom.xml -pl <module> -am test
# Unit + integration tests (Testcontainers; Docker must be running). CI runs this.
mvn -f backend/pom.xml -pl <module> -am verify
```

A new Flyway migration is only proven when the module's integration tests run it against real PostgreSQL in `verify`. Look for `Migrating schema "public" to version "<N> - …"` in the output. A migration that drops data is irreversible in production: call it out in the PR so the release takes a backup first (see the [operations runbook](../docs/deployment/OPERATIONS-RUNBOOK.md)).

Event payload formats are documented in [docs/design/m9/event-payload-schemas.md](../docs/design/m9/event-payload-schemas.md); update it when an event changes.
