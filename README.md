<div align="center">

# 🚀 SwiftDrop — Enterprise Logistics & Courier Management Platform

**Event-driven microservices monorepo • Azure Kubernetes Service • Database-per-Service**

[![CI Backend](https://github.com/BitCraft-Org/SwiftDrop/actions/workflows/ci-backend.yml/badge.svg)](https://github.com/BitCraft-Org/SwiftDrop/actions/workflows/ci-backend.yml)
[![CI Frontend](https://github.com/BitCraft-Org/SwiftDrop/actions/workflows/ci-frontend.yml/badge.svg)](https://github.com/BitCraft-Org/SwiftDrop/actions/workflows/ci-frontend.yml)
[![CD AKS](https://github.com/BitCraft-Org/SwiftDrop/actions/workflows/cd-aks-deploy.yml/badge.svg)](https://github.com/BitCraft-Org/SwiftDrop/actions/workflows/cd-aks-deploy.yml)

</div>

---

## 📐 Architecture Overview

SwiftDrop is built on a **physically isolated Database-per-Service** architecture with **event-driven choreography** via RabbitMQ topic exchanges. Each bounded context owns its schema, lifecycle, and data exclusively.

```
┌──────────────────────────────────────────────────────────────────┐
│                        NGINX Ingress                             │
│                   /api/*  →  api-gateway                         │
│                   /ws/*   →  api-gateway (WebSocket)             │
└────────────────────────────┬─────────────────────────────────────┘
                             │
       ┌─────────────────────┼─────────────────────┐
       ▼                     ▼                     ▼
 ┌───────────┐   ┌───────────────────┐   ┌──────────────────┐
 │ Auth/IAM  │   │  Customer Order   │   │  Pricing & Zone  │
 │  Service  │   │    Service        │   │    Service       │
 └─────┬─────┘   └────────┬──────────┘   └────────┬─────────┘
       │                  │                        │
       ▼                  ▼                        ▼
 ┌───────────┐   ┌───────────────────┐   ┌──────────────────┐
 │ IAM PG DB │   │  Order PG DB     │   │ Pricing PG DB    │
 └───────────┘   └───────────────────┘   └──────────────────┘
                             │
         ┌───────────────────┼───────────────────────┐
         ▼                   ▼                       ▼
  ┌──────────────┐  ┌────────────────┐  ┌───────────────────┐
  │   Dispatch   │  │   Delivery     │  │   Merchant B2B    │
  │   Service    │  │   Service      │  │     Service       │
  └──────┬───────┘  └───────┬────────┘  └────────┬──────────┘
         │                  │                     │
         ▼                  ▼                     ▼
  ┌──────────────┐  ┌────────────────┐  ┌───────────────────┐
  │Dispatch PG DB│  │Delivery PG DB  │  │ Merchant PG DB    │
  └──────────────┘  └────────────────┘  └───────────────────┘
         │
   ┌─────┼──────────────────────┐
   ▼                            ▼
┌──────────────┐  ┌──────────────────────┐  ┌───────────────┐
│  Webhook     │  │  Notification        │  │  Analytics AI │
│  Resiliency  │  │     Service          │  │    Service    │
└──────┬───────┘  └──────────┬───────────┘  └───────┬───────┘
       │                     │                      │
       ▼                     ▼                      ▼
┌──────────────┐  ┌──────────────────────┐  ┌───────────────┐
│Webhook PG DB │  │  Notify PG DB       │  │Analytics PG DB│
└──────────────┘  └──────────────────────┘  └───────────────┘
```

## 🏗️ Monorepo Structure

```
SwiftDrop/
├── .github/workflows/           # CI/CD pipelines
├── docs/                        # ADRs, API specs, design system
├── infra/
│   ├── local/rabbitmq/          # RabbitMQ definitions for local dev
│   └── k8s/
│       ├── base/                # Namespaces, StorageClasses, Ingress
│       ├── databases/           # 9× PostgreSQL StatefulSets (physical isolation)
│       ├── messaging/           # RabbitMQ StatefulSet
│       └── services/            # 10× Microservice Deployments + HPAs
├── services/                    # Backend microservices (Java 25 / Spring Boot 3)
├── apps/                        # Frontend apps (Vite + React + TypeScript)
├── docker-compose.yml           # Local dev: 9 PG instances + RabbitMQ
├── .env.example                 # Environment variable template
└── README.md
```

## 🛢️ Database-per-Service (Physical Isolation)

| Service                     | Database             | K8s StatefulSet               | Storage        |
|-----------------------------|----------------------|-------------------------------|----------------|
| Auth / IAM                  | `swiftdrop_iam`      | `iam-postgres`                | 10Gi Std SSD   |
| Customer Order              | `swiftdrop_order`    | `order-postgres`              | 50Gi Prem SSD  |
| Pricing & Zone              | `swiftdrop_pricing`  | `pricing-postgres`            | 10Gi Std SSD   |
| Dispatch Assignment         | `swiftdrop_dispatch` | `dispatch-postgres`           | 20Gi Prem SSD  |
| Delivery Execution          | `swiftdrop_delivery` | `delivery-postgres`           | 50Gi Prem SSD  |
| Merchant B2B                | `swiftdrop_merchant` | `merchant-postgres`           | 50Gi Prem SSD  |
| Webhook Resiliency          | `swiftdrop_webhook`  | `webhook-postgres`            | 30Gi Prem SSD  |
| Notification                | `swiftdrop_notify`   | `notify-postgres`             | 20Gi Std SSD   |
| Analytics AI                | `swiftdrop_analytics`| `analytics-postgres`          | 50Gi Prem SSD  |

> See [ADR-001](docs/architecture/ADR-001-physical-db-statefulsets.md) for the rationale.

## 🚀 Quick Start (Local Development)

```bash
# 1. Clone and configure
cp .env.example .env

# 2. Spin up infrastructure (9 PostgreSQL + RabbitMQ)
docker compose up -d

# 3. Verify all containers are healthy
docker compose ps

# 4. Access RabbitMQ Management UI
open http://localhost:15672   # user: swiftdrop / pass: changeme_rabbit
```

For the team-member workflow—running only the relevant backend service, its
database and RabbitMQ dependencies, and the frontend—see the
[Local Development Guide](docs/LOCAL-DEVELOPMENT.md).

## 📄 License

Copyright © 2026 BitCraft. All rights reserved.
