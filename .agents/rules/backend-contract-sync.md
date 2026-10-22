---
trigger: glob
globs:
  - "backend/**/*.java"
---

# Backend contract synchronization

Before editing a Java controller, DTO, entity, or RabbitMQ event:

1. Inspect the owning module’s controller/service/entity/repository, `application.yml`, `application-aks.yml`, and Flyway migrations.
2. Inspect [backend/common-contracts](../../backend/common-contracts), especially shared DTO wrappers, enums, event records, and `JwtHeaderConstants`.
3. Inspect [docs/api-specs](../../docs/api-specs) and the gateway route/OpenAPI aggregation.

While coding:

- Use Java 25 records for public request/response DTOs and annotate them with `@Schema`.
- Use `@Tag`, `@Operation`, Jakarta validation, and the existing `ApiResponse<T>` envelope.
- Keep `api-gateway` tracking ownership separate from `delivery-execution-service` FSM/PoD ownership.
- Preserve `eventId`, `correlationId`, `X-Correlation-Id`, exchange, queue, binding, and routing-key semantics.
- Never expose JPA entities directly or create cross-service database foreign keys.

After coding:

- Add a new Flyway migration for entity schema changes.
- Synchronize `RabbitMQConfig.java` and [definitions.json](../../infra/local/rabbitmq/definitions.json) for broker changes.
- Regenerate frontend types with `npm run api:sync:specs` or `npm run api:sync:local`.
- Compile the affected module with `mvn -f backend/pom.xml -pl <module> -am -DskipTests compile`.
