<div align="center">

# 🚀 SwiftDrop

### Enterprise Logistics & Courier Management Platform

**Event-driven microservices monorepo • Azure Kubernetes Service • Database-per-Service**

**Live:** [swiftdrop.bitcraft.biz](https://swiftdrop.bitcraft.biz)

[![CI Backend](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/workflows/ci-backend.yml/badge.svg)](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/workflows/ci-backend.yml)
[![CI Frontend](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/workflows/ci-frontend.yml/badge.svg)](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/workflows/ci-frontend.yml)
[![CD AKS](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/workflows/cd-aks-deploy.yml/badge.svg)](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/workflows/cd-aks-deploy.yml)
[![Security scan](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/workflows/security-scan.yml/badge.svg)](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/actions/workflows/security-scan.yml)

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-FF6600?logo=rabbitmq&logoColor=white)

</div>

---

## Contents

[Overview](#overview) · [Live demo](#live-demo) · [Architecture](#architecture) · [Services and databases](#services-and-databases) · [Events](#event-driven-messaging) · [Parcel lifecycle](#parcel-lifecycle) · [Frontend](#frontend) · [Repository layout](#repository-layout) · [Quick start](#quick-start) · [CI/CD](#cicd) · [Documentation](#documentation) · [Contributing](#contributing)

## Overview

SwiftDrop manages the full life of a courier parcel across Sri Lanka: booking, pricing, dispatch, pickup, delivery with proof of delivery, cash handling, notifications and analytics.

| | |
|---|---|
| **Backend** | Java 25, Spring Boot 3.5, Spring Cloud Gateway (reactive), nine servlet services, Flyway, SpringDoc OpenAPI 3.1 |
| **Messaging** | RabbitMQ topic exchange with dead-letter queues |
| **Data** | PostgreSQL, one physically separate database per service. Redis for JWT blacklist and rate limits |
| **Frontend** | React 18, TypeScript, Vite, Tailwind. One app for five roles, built on the SwiftDrop design system |
| **Platform** | Docker Compose locally; Azure Kubernetes Service (AKS) in production, deployed with a Helm chart behind Traefik and Let's Encrypt TLS |

## Live demo

| | |
|---|---|
| **App** | https://swiftdrop.bitcraft.biz (frontend, plus same-origin `/api` and `/ws`) |
| **API** | https://api.swiftdrop.bitcraft.biz (gateway only; `/actuator` is not exposed) |
| **Runs on** | One AKS node (Standard_B4ms, Azure for Students): 10 Spring services, 9 PostgreSQL StatefulSets, RabbitMQ, Redis, the frontend |
| **Data** | A month of synthetic demo data from [`scripts/seed`](scripts/seed/README.md): about 120 accounts across every role and 240 parcels in every state |

Demo sign-ins are shared with the team in [docs/deployment/DEMO-ACCOUNTS.md](docs/deployment/DEMO-ACCOUNTS.md) and with assessors separately.

> [!NOTE]
> The cluster runs on a student subscription and is paused outside demo times to save credit, so the site may be offline. It is paused and resumed with `scripts/aks/pause.sh` / `resume.sh`, which back up and restore the databases.

## Architecture

Every bounded context owns its schema, lifecycle and data. Services never read each other's databases; they talk through the gateway (HTTP) and RabbitMQ (events).

```mermaid
flowchart TB
    subgraph Clients
        direction LR
        WEB["React app<br/>customer · courier · merchant<br/>dispatcher · system admin"]
        PUB["Public parcel tracking"]
    end

    ING["Traefik ingress · Let's Encrypt TLS<br/>swiftdrop.bitcraft.biz · api.swiftdrop.bitcraft.biz"]
    GW["<b>api-gateway</b> :8080<br/>JWT · CORS · rate limiting<br/>tracking hub (SSE / WebSocket)"]
    REDIS[("Redis<br/>JWT blacklist · rate limits")]

    subgraph G1["Identity and ordering"]
        AUTH["auth-iam-service :8081<br/><i>swiftdrop_iam</i>"]
        ORDER["customer-order-service :8082<br/><i>swiftdrop_order</i>"]
        PRICE["pricing-zone-service :8083<br/><i>swiftdrop_pricing</i>"]
    end
    subgraph G2["Fulfilment"]
        DISP["dispatch-assignment-service :8084<br/><i>swiftdrop_dispatch</i>"]
        DELIV["delivery-execution-service :8085<br/><i>swiftdrop_delivery</i>"]
        MERCH["merchant-b2b-service :8086<br/><i>swiftdrop_merchant</i>"]
    end
    subgraph G3["Platform"]
        HOOK["webhook-resiliency-service :8087<br/><i>swiftdrop_webhook</i>"]
        NOTE["notification-service :8088<br/><i>swiftdrop_notify</i>"]
        ANA["analytics-ai-service :8089<br/><i>swiftdrop_analytics</i>"]
    end

    MQ{{"RabbitMQ<br/>swiftdrop.events.exchange"}}

    WEB --> ING
    PUB --> ING
    ING --> GW
    GW -.-> REDIS
    GW -- "REST" --> G1
    GW -- "REST" --> G2
    GW -- "REST" --> G3
    G1 <-- "events" --> MQ
    G2 <-- "events" --> MQ
    G3 <-- "events" --> MQ
    MQ -- "parcel status stream" --> GW
```

> [!NOTE]
> The browser only ever talks to the gateway: `http://localhost:8080/api/v1/...` locally, and same-origin `/api/v1/...` on https://swiftdrop.bitcraft.biz. Downstream service ports are for local debugging, not for the frontend.

## Services and databases

| Service | Port | Database | Local DB port | K8s StatefulSet | Storage | Responsibility |
|---|---:|---|---:|---|---|---|
| `api-gateway` | 8080 | none (stateless) | n/a | n/a | n/a | Routing, JWT and header propagation, CORS, rate limiting, tracking hub |
| `auth-iam-service` | 8081 | `swiftdrop_iam` | 5431 | `iam-postgres` | 10Gi Std SSD | Authentication, identity, roles |
| `customer-order-service` | 8082 | `swiftdrop_order` | 5433 | `order-postgres` | 50Gi Prem SSD | Customer order lifecycle |
| `pricing-zone-service` | 8083 | `swiftdrop_pricing` | 5434 | `pricing-postgres` | 10Gi Std SSD | Pricing quotes, geofence zones |
| `dispatch-assignment-service` | 8084 | `swiftdrop_dispatch` | 5435 | `dispatch-postgres` | 20Gi Prem SSD | Courier availability and assignment |
| `delivery-execution-service` | 8085 | `swiftdrop_delivery` | 5436 | `delivery-postgres` | 50Gi Prem SSD | Delivery state machine, proof of delivery, location events |
| `merchant-b2b-service` | 8086 | `swiftdrop_merchant` | 5437 | `merchant-postgres` | 50Gi Prem SSD | Merchant APIs, API keys, order ingestion |
| `webhook-resiliency-service` | 8087 | `swiftdrop_webhook` | 5438 | `webhook-postgres` | 30Gi Prem SSD | Webhook subscriptions, retries, dead-letter replay |
| `notification-service` | 8088 | `swiftdrop_notify` | 5439 | `notify-postgres` | 20Gi Std SSD | Notification preferences and logs |
| `analytics-ai-service` | 8089 | `swiftdrop_analytics` | 5440 | `analytics-postgres` | 50Gi Prem SSD | Operational KPIs, AI delivery assistant |

Storage sizes are the production Helm profile (Azure managed disks). The student profile used for the live demo gives every database a 2Gi `local-path` volume on the node's own disk, because a 4-vCPU VM can attach only 8 managed disks and SwiftDrop needs 11 volumes.

Shared event records, DTO wrappers, enums and security headers live in [`backend/common-contracts`](backend/common-contracts). See [ADR-001](docs/architecture/ADR-001-physical-db-statefulsets.md) for why every database is its own StatefulSet.

## Event-driven messaging

Services publish to one topic exchange, `swiftdrop.events.exchange`. Each consumer binds its own queue by routing key. Messages that keep failing are dead-lettered to `swiftdrop.dlx.exchange` (`dlq.*` queues) so nothing is silently lost.

```mermaid
flowchart LR
    EX{{"swiftdrop.events.exchange<br/>topic"}}
    DLX{{"swiftdrop.dlx.exchange<br/>dead letters"}}

    EX -- "order.#" --> Q1["order.events.queue"] --> S1["customer-order-service"]
    EX -- "order.created<br/>order.cancelled<br/>parcel.status.changed.#<br/>dispatch.#" --> Q2["dispatch.* queues"] --> S2["dispatch-assignment-service"]
    EX -- "delivery.#<br/>dispatch.courier.assigned" --> Q3["delivery.* queues"] --> S3["delivery-execution-service"]
    EX -- "notification.#" --> Q4["notification.events.queue"] --> S4["notification-service"]
    EX -- "order.created<br/>parcel.status.changed.#<br/>webhook.dispatch.requested" --> Q5["webhook.events.queue"] --> S5["webhook-resiliency-service"]
    EX -- "parcel.status.changed.#<br/>dispatch.#" --> Q6["analytics.events.queue"] --> S6["analytics-ai-service"]
    EX -- "parcel.status.changed.#<br/>parcel.fee.revised" --> Q7["swiftdrop.gateway.* queues"] --> S7["api-gateway<br/>tracking hub"]

    Q1 & Q2 & Q3 & Q4 & Q5 & Q6 -. "failed after retries" .-> DLX
```

The queue, exchange and binding definitions for local development are in [`infra/local/rabbitmq/definitions.json`](infra/local/rabbitmq/definitions.json). Keep that file, the owning `RabbitMQConfig.java` and the event records in `common-contracts` in sync.

## Parcel lifecycle

The delivery service owns the state machine. Only allowed transitions are accepted, and `DELIVERED` needs verified proof of delivery (a photo plus recipient name, or a signature).

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> ASSIGNED: courier assigned
    CREATED --> CANCELLED
    ASSIGNED --> PICKED_UP: pickup confirmed
    ASSIGNED --> CANCELLED
    ASSIGNED --> FAILED
    PICKED_UP --> IN_TRANSIT
    PICKED_UP --> FAILED
    IN_TRANSIT --> OUT_FOR_DELIVERY
    IN_TRANSIT --> FAILED
    OUT_FOR_DELIVERY --> DELIVERED: proof of delivery verified
    OUT_FOR_DELIVERY --> FAILED
    DELIVERED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]
```

Customers see a friendlier set of **nine public statuses**. They map one to one with the states above, with two differences: `ASSIGNED` is shown as `PICKUP_ASSIGNED`, and `RETURNED_TO_SENDER` exists only as a public status (the delivery state machine has no return state).

How a booking flows through the system:

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant GW as api-gateway
    participant O as customer-order
    participant MQ as RabbitMQ
    participant D as dispatch-assignment
    participant X as delivery-execution
    actor K as Courier

    C->>GW: POST /api/v1/orders (JWT)
    GW->>O: route request
    O-->>MQ: order.created
    MQ-->>D: order.created
    D-->>MQ: dispatch.courier.assigned
    MQ-->>X: dispatch.courier.assigned
    K->>GW: transition parcel state (e.g. PICKED_UP)
    GW->>X: route request
    X-->>MQ: parcel.status.changed.*
    MQ-->>GW: parcel.status.changed.*
    GW-->>C: sanitised live update (SSE / WebSocket)
```

## Frontend

One React app serves five roles. After sign-in, `AppShell` picks the matching layout: a side navigation and top bar on desktop, and a bottom tab bar on mobile for customers and couriers.

| Role | Density | Shell | Typical screens |
|---|---|---|---|
| Customer | Comfortable (44px controls) | Side nav, mobile tabs | Book, track |
| Courier | Kinetic (56px thumb targets) | Side nav, mobile tabs | Shift, active delivery |
| Merchant | Compact (34px) | Side nav | Dashboard, API keys, B2B orders, webhooks, registration |
| Dispatcher | Compact | Side nav | Control tower, dispatch board, webhook dead letters |
| System admin | Compact | Side nav | The ops area (`/ops`) plus merchant approvals, users, zone and tariff pricing, analytics dashboard, notification console |

### SwiftDrop design system

The UI is built from the **SwiftDrop** design system, which mirrors the [Figma file](https://www.figma.com/design/XX3u7zxTDat0l2MbG27niR/BitCraft-SwiftDrop-Web-Design--Copy-). The Figma file is the source of truth; the theme is **light only**.

```mermaid
flowchart LR
    F["Figma<br/>variables and components"] --> T["Design tokens<br/>CSS variables · Tailwind · JSON"]
    T --> C["Components<br/>src/design-system"]
    C --> K["Public catalog<br/>/catalog"]
    C --> A["App shells and pages<br/>AppShell · role pages"]
```

- **Browse every component** at [`/catalog`](http://localhost:5173/catalog) once the frontend is running. It is public: no login and no backend needed. It is grouped like the Figma library, with a gallery that shows the shell of every role at desktop, tablet and mobile width.
- **Share the catalog as one file:** `npm run build:catalog` writes a single self-contained HTML file you can open from disk or send to someone.
- **Component rules and the full component map:** [`frontend/COMPONENTS.md`](frontend/COMPONENTS.md). Figma links: [`docs/design-system/figma-source.md`](docs/design-system/figma-source.md).

### Working on the frontend

```bash
cd frontend
npm install
npm run dev            # http://localhost:5173
npm run lint           # type-check
npm run build          # production build
npm run build:catalog  # single-file design catalog
npm run seed:demo      # create demo accounts per role (local only)
npm run api:sync:specs # regenerate API types from docs/api-specs
```

> [!TIP]
> No backend? Use **Continue in development mode** on the login page, then the *Dev persona* bar to switch roles. It uses a fake token, so API calls fail, but layouts and navigation work. For a real sign-in per role, run `npm run seed:demo`.

## Repository layout

```text
SwiftDrop/
├── backend/                     Maven reactor (Java 25, Spring Boot 3.5)
│   ├── common-contracts/        Shared events, DTOs, enums, headers
│   ├── api-gateway/             Routing, JWT, tracking hub
│   └── *-service/               Nine business services
├── frontend/                    Vite + React + TypeScript app
│   └── src/
│       ├── design-system/       Tokens, primitives, components, shells
│       ├── features/            customer · courier · merchant · admin-ops · auth · catalog
│       └── app/                 Router, AppShell, providers
├── infra/
│   ├── local/rabbitmq/          Queue, exchange and binding definitions
│   ├── helm/swiftdrop/          Helm chart for AKS (all services, DBs, broker, ingress)
│   └── cluster/                 One-time cluster add-on values (Traefik)
├── scripts/
│   ├── seed/                    Demo-data seeder (drives the public APIs) and timeshift
│   ├── db/                      AKS database backup / restore
│   └── aks/                     Pause / resume the student cluster without losing data
├── docs/                        API specs, ADRs, deployment guides, design system
├── .github/workflows/           CI and CD pipelines
├── docker-compose.yml           Local infrastructure and optional app containers
└── .env.example                 Environment variable template
```

## Quick start

**Prerequisites:** Java 25, Maven 3.9+, Node.js 20+, Docker Desktop.

```bash
# 1. Configure
cp .env.example .env

# 2. Start infrastructure (RabbitMQ, Redis and the 9 PostgreSQL databases)
docker compose up -d
docker compose ps

# 3. Run the frontend
cd frontend && npm install && npm run dev
```

To run services too, either start them from your IDE or Maven (see the [Local Development Guide](docs/LOCAL-DEVELOPMENT.md)), or use containers:

```bash
docker compose --profile apps up -d --build
```

> [!WARNING]
> The full stack is heavy: ten JVMs plus databases. With about 4 GB given to Docker the gateway was killed for lack of memory. Give Docker **6 GB or more**, or run only the services you need. Normal feature work needs just your service, its database and RabbitMQ.

| What | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Design catalog | http://localhost:5173/catalog |
| API gateway | http://localhost:8080 |
| Swagger UI (all services) | http://localhost:8080/swagger-ui.html |
| RabbitMQ management | http://localhost:15672 (`swiftdrop` / `changeme_rabbit`) |

## CI/CD

```mermaid
flowchart LR
    PR["Pull request"] --> CB["CI Backend<br/>Java 25 · compile · test · verify"]
    PR --> CF["CI Frontend<br/>Node 20 · type-check · lint · build"]
    PR --> CH["CI Helm<br/>lint · template · schema validation"]
    PR --> SEC["Security scan<br/>CodeQL · Trivy deps and config"]
    MAIN["Push to main"] --> DET["Detect changed components"]
    DET --> BP["Build images on GitHub runners<br/>Trivy gate (HIGH/CRITICAL) · push to ACR"]
    BP --> APP{{"Approval<br/>production environment"}}
    APP --> DEP["helm upgrade --install --wait<br/>on AKS"]
```

Workflows are in [`.github/workflows`](.github/workflows).
- **Builds run on GitHub runners**, never on the cluster. AKS only pulls the finished images from ACR.
- **Deployment is incremental**: only changed components are rebuilt, and the rest keep the image they are running. A manual run can target one component, `all` (needed for the first deploy), or `none` (config only).
- **Security gates**: an image with a fixable HIGH or CRITICAL CVE is never pushed. Accepted exceptions live in [`.trivyignore.yaml`](.trivyignore.yaml), each with a reason and an expiry date.
- **Access**: GitHub logs in to Azure with OIDC (no stored password). The Helm secrets are a `production` environment secret, so only `main` can deploy, and every deploy waits for a reviewer.

The full setup, from creating the Azure resources to the first deploy, is in the [AKS Helm deployment guide](docs/deployment/AKS-HELM-DEPLOYMENT.md).

## Documentation

| Doc | What it covers |
|---|---|
| [Local Development Guide](docs/LOCAL-DEVELOPMENT.md) | Run one service, its database and the frontend |
| [AKS Helm Deployment Guide](docs/deployment/AKS-HELM-DEPLOYMENT.md) | Deploy the full system to Azure Kubernetes Service with the Helm chart |
| [Local Kubernetes Guide](docs/deployment/LOCAL-KUBERNETES.md) | Run the same chart for free on Docker Desktop's Kubernetes |
| [Demo data seeder](scripts/seed/README.md) | Fill a cluster with a month of realistic data through the public APIs |
| [Demo accounts](docs/deployment/DEMO-ACCOUNTS.md) | Seeded sign-ins per role, for the team |
| [API contracts](docs/api-specs/README.md) | OpenAPI documents, gateway routes, generating frontend types |
| [ADR-001](docs/architecture/ADR-001-physical-db-statefulsets.md) | Why every database is a separate StatefulSet |
| [Component guide](frontend/COMPONENTS.md) | Design system rules, components, catalog routes |
| [Design system source](docs/design-system/figma-source.md) | Figma file and node links |
| [Agent instructions](AGENTS.md) | Architecture boundaries and the change-sync checklist |

## Contributing

- Branch from `dev` and open pull requests into `dev`. Never commit to `dev` or `main` directly.
- Releases are a `dev` → `main` pull request. Pushing to `main` starts the CD pipeline; the deploy waits for approval.
- Keep database ownership strict: no cross-service foreign keys, shared tables or direct database access.
- Changing a REST contract, event or database entity has required follow-up steps; see the matrix in [`AGENTS.md`](AGENTS.md).
- Before opening a frontend PR: `npm run lint` and `npm run build`.

## License

Copyright © 2026 BitCraft. All rights reserved.
