---
trigger: glob
globs:
  - "docker-compose.yml"
  - "infra/**/*"
---

# Infrastructure and database isolation

Before editing infrastructure, inspect [docker-compose.yml](../../docker-compose.yml), [RabbitMQ definitions](../../infra/local/rabbitmq/definitions.json), the Helm chart [values.yaml](../../infra/helm/swiftdrop/values.yaml) (namespaces, the target service's `services.<name>` entry, `postgres.databases`), the relevant template under [infra/helm/swiftdrop/templates](../../infra/helm/swiftdrop/templates), and the owning service’s `application.yml`/`application-aks.yml`.

- The Helm chart [infra/helm/swiftdrop](../../infra/helm/swiftdrop) is the single source of truth for Kubernetes. Do not add raw manifests beside it.
- Preserve the nine physical PostgreSQL databases (one StatefulSet each) and their service ownership. `api-gateway` is stateless.
- Preserve the `swiftdrop-data`, `swiftdrop-apps`, and `swiftdrop-messaging` namespace split.
- Preserve each StatefulSet’s headless Service, PVC template, per-profile storage class, and database-specific credentials (`<db>-postgres-credentials`).
- Keep local host port/database parity with service JDBC configuration.
- Keep RabbitMQ exchange, queue, binding, durability, and routing-key declarations synchronized with Java `RabbitMQConfig.java`.
- Any new environment variable must be mirrored in `.env.example`, Docker Compose, and the chart: non-secret values in `services.<name>.config`, secrets in `services.<name>.secretEnv` plus `templates/secrets.yaml`, `values-secrets.example.yaml` and `scripts/generate-helm-secrets.sh`.
- Never commit real secret values; `infra/helm/swiftdrop/values-secrets.yaml` is git-ignored.
- Gateway tracking uses `swiftdrop.events.exchange`, queue `swiftdrop.gateway.tracking.queue`, and binding `parcel.status.changed.#`.

After editing:

```powershell
docker compose config --quiet
docker compose ps

helm lint infra/helm/swiftdrop -f infra/helm/swiftdrop/values-secrets.example.yaml
helm lint infra/helm/swiftdrop -f infra/helm/swiftdrop/values-student.yaml -f infra/helm/swiftdrop/values-secrets.example.yaml
helm template swiftdrop infra/helm/swiftdrop -f infra/helm/swiftdrop/values-secrets.example.yaml > rendered.yaml
```

Also parse/validate changed YAML/JSON and verify the rendered objects still target the correct namespace, service port, headless DNS, and PVC. CI ([ci-helm.yml](../../.github/workflows/ci-helm.yml)) repeats the lint and validates the rendered manifests against the Kubernetes schemas.
