# ADR-001: Physical Database Isolation via Kubernetes StatefulSets

| Field        | Value                                                |
|--------------|------------------------------------------------------|
| **Status**   | Accepted                                             |
| **Date**     | 2026-10-21                                           |
| **Authors**  | SwiftDrop Platform Team                              |
| **Deciders** | Principal Architect, Lead SRE, Engineering Manager   |

## Context

SwiftDrop is an event-driven logistics and courier management platform composed of **10 microservices** and **9 bounded contexts**, each requiring persistent state. The team evaluated two database provisioning strategies:

1. **Logical Isolation** — A single shared PostgreSQL cluster with per-service logical databases.
2. **Physical Isolation** — Dedicated PostgreSQL instances (one per service), each running in its own Kubernetes StatefulSet with independent PersistentVolumeClaims.

## Decision

We adopt **Physical Database Isolation** — 9 dedicated PostgreSQL 16 StatefulSets deployed in the `swiftdrop-data` namespace on Azure Kubernetes Service (AKS).

Each StatefulSet provisions:
- A **Headless Service** (`clusterIP: None`) for stable DNS-based pod identity.
- A **`volumeClaimTemplates`** block backed by Azure Managed Disk StorageClasses (`managed-csi-premium` or `managed-csi-standard`).
- Independent **resource requests and limits** tuned to the service's workload profile.

### Database Allocation

| Service                     | StatefulSet               | Storage Class         | Capacity |
|-----------------------------|---------------------------|-----------------------|----------|
| Auth / IAM                  | `iam-postgres`            | `managed-csi-standard`| 10Gi     |
| Customer Order              | `order-postgres`          | `managed-csi-premium` | 50Gi     |
| Pricing & Zone              | `pricing-postgres`        | `managed-csi-standard`| 10Gi     |
| Dispatch Assignment         | `dispatch-postgres`       | `managed-csi-premium` | 20Gi     |
| Delivery Execution          | `delivery-postgres`       | `managed-csi-premium` | 50Gi     |
| Merchant B2B                | `merchant-postgres`       | `managed-csi-premium` | 50Gi     |
| Webhook Resiliency          | `webhook-postgres`        | `managed-csi-premium` | 30Gi     |
| Notification                | `notify-postgres`         | `managed-csi-standard`| 20Gi     |
| Analytics AI                | `analytics-postgres`      | `managed-csi-premium` | 50Gi     |

## Rationale

### Why Physical Isolation?

1. **Blast Radius Containment** — A runaway query in the analytics service cannot starve the dispatch service of CPU or I/O. Each instance is a separate Linux process with its own `shared_buffers`, `work_mem`, and connection pool.

2. **Independent Scaling** — High-throughput services (Order, Delivery, Merchant) run on Premium SSD–backed volumes, while lower-traffic services (IAM, Pricing, Notification) use cost-effective Standard SSDs. Scaling storage or compute for one service has zero impact on others.

3. **Independent Lifecycle Management** — Schema migrations, major PostgreSQL upgrades, and point-in-time restores can be performed per-service without coordinating a global maintenance window.

4. **Security & Compliance** — Each database runs under its own Kubernetes ServiceAccount and NetworkPolicy. Credentials are scoped to a single bounded context, limiting the blast radius of a credential leak.

5. **Operational Observability** — Per-instance metrics (via `pg_stat_statements`, Prometheus `postgres_exporter`) provide unambiguous attribution of slow queries, connection exhaustion, and replication lag to a specific service.

### Trade-offs Acknowledged

| Concern                      | Mitigation                                                        |
|------------------------------|-------------------------------------------------------------------|
| Higher resource baseline     | Right-sized resource requests; Standard SSD for low-traffic DBs  |
| Operational complexity       | Templated StatefulSet manifests; GitOps via Flux/ArgoCD          |
| Cross-service queries        | Event-driven data replication (CQRS read models in Analytics)    |
| Backup coordination          | Azure Disk snapshots scheduled independently per volume          |

## Consequences

- All inter-service data access MUST go through published domain events on the `swiftdrop.events.exchange` RabbitMQ topic exchange. Direct cross-database queries are **prohibited**.
- The Analytics AI service maintains denormalized read models populated via event consumers, enabling cross-domain reporting without violating service boundaries.
- Database connection strings are injected via Kubernetes Secrets, one per service, managed by Azure Key Vault CSI driver.

## Alternatives Considered

### Shared PostgreSQL with Logical Databases
Rejected due to shared resource contention ("noisy neighbor"), inability to independently scale storage tiers, and risk of cascading failures during schema migrations.

### Managed Azure Database for PostgreSQL Flexible Server
Considered for production maturity but deferred to Phase 2. Current StatefulSet approach provides parity between local development (`docker-compose.yml` with 9 isolated containers) and AKS, reducing environment drift.

## References

- [Microsoft: Database-per-service pattern](https://learn.microsoft.com/en-us/azure/architecture/microservices/design/data-considerations)
- [Chris Richardson: Database per Service](https://microservices.io/patterns/data/database-per-service.html)
- [Kubernetes StatefulSets](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
