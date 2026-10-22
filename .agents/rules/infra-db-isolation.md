---
trigger: glob
globs:
  - "docker-compose.yml"
  - "infra/**/*"
---

# Infrastructure and database isolation

Before editing infrastructure, inspect [docker-compose.yml](../../docker-compose.yml), [RabbitMQ definitions](../../infra/local/rabbitmq/definitions.json), namespace declarations, the target service manifest, and the owning service’s `application.yml`/`application-aks.yml`.

- Preserve the nine physical PostgreSQL databases and their service ownership. `api-gateway` is stateless.
- Preserve the `swiftdrop-data`, `swiftdrop-apps`, and `swiftdrop-messaging` namespace split.
- Preserve each StatefulSet’s headless Service, PVC template, storage class, and database-specific credentials.
- Keep local host port/database parity with service JDBC configuration.
- Keep RabbitMQ exchange, queue, binding, durability, and routing-key declarations synchronized with Java `RabbitMQConfig.java`.
- Any new environment variable must be mirrored in `.env.example`, Docker Compose, and the corresponding AKS service manifest.
- Gateway tracking uses `swiftdrop.events.exchange`, queue `swiftdrop.gateway.tracking.queue`, and binding `parcel.status.changed.#`.

After editing:

```powershell
docker compose config --quiet
docker compose ps
```

Also parse/validate changed YAML/JSON and verify the affected Kubernetes manifest still targets the correct namespace, service port, headless DNS, and PVC.
