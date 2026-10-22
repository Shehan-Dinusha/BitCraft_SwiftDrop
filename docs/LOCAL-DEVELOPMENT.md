# SwiftDrop Local Development Guide

This guide is for team members developing one backend service and/or the React frontend locally. You do not need to run the entire microservice platform for normal feature work.

## 1. Prerequisites

Install the following:

- Java 25 LTS
- Maven 3.9 or newer
- Node.js 20 or newer
- npm
- Docker Desktop
- VS Code with Extension Pack for Java and Spring Boot Extension Pack

Verify the tools from PowerShell:

```powershell
java -version
mvn -version
node --version
npm --version
docker --version
docker compose version
```

Open the repository root in VS Code. Do not open only a service folder:

```text
<repository-root>
```

The root contains [backend/pom.xml](../backend/pom.xml), which is the Maven reactor parent.

## 2. First-time setup

From the repository root:

```powershell
cd "<repository-root>"
```

Create a local environment file if one is provided:

```powershell
Copy-Item .env.example .env
```

Install frontend dependencies:

```powershell
Set-Location frontend
npm install
Set-Location ..
```

Build and install the shared backend contracts:

```powershell
mvn -f backend\pom.xml -pl common-contracts -am -DskipTests install
```

Repeat the shared-contracts install whenever files under [common-contracts](../backend/common-contracts) change.

## 3. Start only the infrastructure you need

Every database-backed service requires its own PostgreSQL container. Services that publish or consume events also require RabbitMQ.

Start only the dependencies for your service. For example, delivery execution:

```powershell
docker compose up -d rabbitmq delivery-postgres
docker compose ps rabbitmq delivery-postgres
```

RabbitMQ Management UI:

```text
http://localhost:15672
```

Default local RabbitMQ credentials:

```text
Username: swiftdrop
Password: changeme_rabbit
```

The default local PostgreSQL credentials are:

```text
Username: swiftdrop
Password: swiftdrop
```

## 4. Backend service matrix

Use the module name in the `mvn` command and start the matching PostgreSQL container first.

| Team area | Maven module | Application port | PostgreSQL container | PostgreSQL host port |
|---|---|---:|---|---:|
| API gateway and tracking hub | `api-gateway` | 8080 | None | None |
| Auth and IAM | `auth-iam-service` | 8081 | `iam-postgres` | 5432 |
| Customer orders | `customer-order-service` | 8082 | `order-postgres` | 5433 |
| Pricing and zones | `pricing-zone-service` | 8083 | `pricing-postgres` | 5434 |
| Dispatch and assignment | `dispatch-assignment-service` | 8084 | `dispatch-postgres` | 5435 |
| Delivery execution | `delivery-execution-service` | 8085 | `delivery-postgres` | 5436 |
| Merchant B2B | `merchant-b2b-service` | 8086 | `merchant-postgres` | 5437 |
| Webhook resiliency | `webhook-resiliency-service` | 8087 | `webhook-postgres` | 5438 |
| Notifications | `notification-service` | 8088 | `notify-postgres` | 5439 |
| Analytics and AI | `analytics-ai-service` | 8089 | `analytics-postgres` | 5440 |

## 5. Run one backend service

Run these commands from the repository root.

### API gateway

```powershell
mvn -f backend\pom.xml -pl api-gateway -am spring-boot:run
```

The gateway runs at `http://localhost:8080`.

### Delivery execution

```powershell
docker compose up -d rabbitmq delivery-postgres
mvn -f backend\pom.xml -pl delivery-execution-service -am spring-boot:run
```

The delivery service runs at `http://localhost:8085`.

### Any other service

Replace `<module>` and `<database-container>` with the values from the service matrix:

```powershell
docker compose up -d rabbitmq <database-container>
mvn -f backend\pom.xml -pl <module> -am spring-boot:run
```

For example, notification development:

```powershell
docker compose up -d rabbitmq notify-postgres
mvn -f backend\pom.xml -pl notification-service -am spring-boot:run
```

Useful endpoints for a running service:

```text
http://localhost:<application-port>/actuator/health
http://localhost:<application-port>/v3/api-docs
http://localhost:<application-port>/swagger-ui.html
```

## 6. Run the frontend

Start the frontend in a separate terminal:

```powershell
cd "<repository-root>\frontend"
npm run dev
```

The Vite development server normally runs at:

```text
http://localhost:5173
```

Frontend REST and real-time clients use the API gateway at `http://localhost:8080`. Therefore, frontend feature work normally requires:

1. RabbitMQ and the database required by the target service.
2. The target backend service.
3. The API gateway.
4. The frontend Vite server.

Run frontend checks with:

```powershell
npm run lint
npm run build
```

Regenerate TypeScript API declarations from checked-in OpenAPI specifications:

```powershell
npm run api:sync:specs
```

Regenerate them from live gateway OpenAPI documents when the gateway and required services are running:

```powershell
npm run api:sync:local
```

## 7. Recommended workflows by team member

### Backend-only service work

Use two terminals:

```powershell
# Terminal 1
docker compose up -d rabbitmq <database-container>

# Terminal 2
mvn -f backend\pom.xml -pl <module> -am spring-boot:run
```

Compile without starting the service:

```powershell
mvn -f backend\pom.xml -pl <module> -am -DskipTests compile
```

Run module tests:

```powershell
mvn -f backend\pom.xml -pl <module> -am test
```

### Frontend feature work

If the feature uses only local UI state:

```powershell
cd frontend
npm run dev
```

If it calls an API, also run the relevant backend, its infrastructure, and the gateway.

### Gateway and tracking work

Start RabbitMQ and the gateway:

```powershell
docker compose up -d rabbitmq
mvn -f backend\pom.xml -pl api-gateway -am spring-boot:run
```

For delivery tracking event testing, also start:

```powershell
docker compose up -d delivery-postgres
mvn -f backend\pom.xml -pl delivery-execution-service -am spring-boot:run
```

## 8. Gateway routing note

The gateway route definitions use service-discovery URIs such as `lb://delivery-execution-service`. This is correct for the containerized/Kubernetes deployment topology, but a gateway started directly with Maven may not discover another service started directly with Maven unless local service discovery is configured.

For direct backend testing, call the service port directly, for example:

```text
http://localhost:8085
```

For browser testing, use the gateway at `http://localhost:8080`. If a gateway route returns a service-discovery or connection error during direct-Maven development, use the service directly for backend testing or run the Docker/Kubernetes topology that provides service discovery.

## 9. Stop local processes and infrastructure

Stop a Maven-run service with `Ctrl+C` in its terminal.

Stop containers without deleting local data:

```powershell
docker compose stop
```

Remove containers but preserve named volumes:

```powershell
docker compose down
```

Remove containers and all local database/message data:

```powershell
docker compose down -v
```

Use `down -v` only when you intentionally want a clean local database.

## 10. VS Code Maven refresh and troubleshooting

When a `pom.xml` changes:

1. Press `Ctrl+Shift+P`.
2. Run **Java: Update Project Configuration**.
3. If classpath warnings remain, run **Java: Clean Java Language Server Workspace**.
4. Confirm the reload.
5. Run **Java: Update Project Configuration** again.

If Maven reports that `common-contracts` cannot be resolved, run:

```powershell
mvn -f backend\pom.xml -pl common-contracts -am -DskipTests install
```

If a database connection fails, verify that the correct PostgreSQL container is running and that the host port matches the service matrix.

If RabbitMQ connection fails, verify:

```powershell
docker compose ps rabbitmq
```

## 11. Full local infrastructure

Use this only when testing cross-service behavior:

```powershell
docker compose up -d
docker compose ps
```

This starts RabbitMQ, the API gateway container, and all nine PostgreSQL containers. Backend application services are normally run individually with Maven unless you intentionally use the containerized deployment workflow.
