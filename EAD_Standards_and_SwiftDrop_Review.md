# Enterprise Application Development (IN3100): Standards Guide and SwiftDrop Compliance Review

This document has two parts:

- **Part A** pulls the key points out of the seven EAD lectures (Lecture 1, 2, 3, 4, 5, 6.1, 6.2) and turns them into a checklist for building an enterprise project.
- **Part B** checks the **BitCraft SwiftDrop** project against that checklist. Each item gets a status, the evidence, and a fix where something is missing.

> No files in the SwiftDrop project were changed during this review.

---

# PART A: Key Points From the Lectures

## 1. What an enterprise application is (Lecture 1)

An enterprise application is a large software system built for the complex needs of an organisation. It supports and automates business processes, improves efficiency, and helps employees, customers and partners work together. Examples are ERP, CRM, and BI/analytics systems.

In short it is **complex, business-oriented, and made of many components**.

### The five key characteristics every enterprise app must have

| # | Characteristic | What it means in practice |
|---|---|---|
| 1 | **Scalability** | Handles many users and a lot of data, and grows with the organisation |
| 2 | **Integration** | Connects to existing systems, databases and third-party services |
| 3 | **Security** | Protects sensitive business and customer data |
| 4 | **Performance** | Serves many users and processes at the same time |
| 5 | **Reliability** | Stays available, with no downtime and no data loss |

### Course roadmap (the expected end result)
1. Basic concepts of enterprise application development
2. Component architectures
3. Layered architecture: Presentation layer, Business logic layer, Database layer
4. Integration of enterprise applications
5. Web application development frameworks
6. Design patterns and reusability
7. Security of enterprise applications

**By the end the module expects you to:**
- Build a backend with **Spring Boot**
- Deploy it with **Docker**
- Deploy it to a **Kubernetes cluster**
- Authenticate with **JWT / OAuth2**
- Implement **inter-component communication**

### Framework or pure language?
- Don't reinvent the wheel; use proven frameworks and libraries.
- Before adopting a framework or library, check its **License**, **Usage** (is it a good fit), and **Community** (support, maintenance).

### Backend For Frontend (BFF)
- Put a dedicated backend layer between clients and the internal services, instead of letting clients call every service directly.

### APIs
- An API is how a server provides the information a client asks for.
- Types: **REST**, **GraphQL**, **RPC**, **MCP** (how AI agents talk to each other and to tools).

---

## 2. Virtual Machines vs Containers (Lecture 2)

### Virtual Machines
- A VM emulates a full physical computer and runs its own OS.
- Traits: isolation, hardware emulation, portability, resource allocation, snapshots, multiple OSes.
- Problems: **resource-hungry, heavy (whole OS), hard to move.**
- Examples: VMware, VirtualBox, Hyper-V, KVM.

### Containers (the preferred approach)
A container is a lightweight, portable package holding code, runtime, system tools, libraries and settings. It is built from an **image**, a standalone, executable package that holds everything needed to run the app.

| Container characteristic | Practice it implies |
|---|---|
| Isolation | Each service in its own container |
| Lightweight | Shares the host kernel; use slim base images |
| Portability | The same image runs in dev, test and prod |
| Consistency | The same environment at every stage of the SDLC |
| Immutability | Images never change after build, so no configuration drift |
| Dependency management | Everything is bundled, which ends "it works on my machine" |
| Scalability | Scale up or down fast, especially with **Kubernetes** |
| Fast deployment | Starts and stops in seconds |
| Versioning and rollback | Tag images by version and roll back to an older image |

**Load balancing:** spread traffic across many container instances.

---

## 3. Software Architectures (Lecture 2)

| Architecture | Core idea | Pros | Cons | Use when |
|---|---|---|---|---|
| **Monolithic** | One codebase, one deployable unit | Simple, fast to build, easy to debug, low overhead | Hard to scale, bloats over time, inflexible, a full redeploy for every change, team coordination problems | Small or early projects |
| **Client-Server** | Client (UI) sends requests; server runs logic and holds data | Server scales on its own, central data, sensitive work stays on the server, easy updates | Depends on the network, the server can become a bottleneck, more complex | Almost every web or mobile system |
| **Microservices** | Small, loosely coupled, independently deployable services, each owning one business capability, talking through well-defined APIs | Decentralised, independent, loosely coupled, scales per service, mixed technologies, resilient, CI/CD friendly, team autonomy, flexible deployment | Network overhead, **data consistency across services**, operational complexity, service coordination, initial overhead | Large systems with many teams (Netflix, Amazon, Uber) |
| **SOA** | Reusable, loosely coupled, self-contained services | Reuse, interoperability, scalability, modularity, legacy integration | Complexity, performance overhead, governance challenges | Integrating many heterogeneous or legacy systems |
| **Event-Driven (EDA)** | Components publish and react to events through an **event bus / broker** | Flexible, real-time, loosely coupled, modular, easy integration | Hard to trace and debug, event overhead, **ordering and consistency** problems | Real-time updates, notifications, IoT, e-commerce order flows |
| **Layered (n-tier)** | Presentation → Business → Data layers; each layer serves the one above it | Modular, separation of concerns, maintainable, reusable, scalable | Can be rigid, adds overhead between layers | Inside almost every service |
| **Serverless (FaaS)** | Cloud runs functions triggered by events | No infrastructure to manage, auto-scaling, pay per use, fast deployment | Cold starts, resource limits, stateless only, vendor lock-in | Short event-driven tasks, scheduled jobs, chatbots |

**EDA key concepts to implement:** events, producers, consumers, event bus/broker, publish-subscribe, event handlers, asynchronous communication.

---

## 4. Coding Best Practices and Principles (Lecture 3 and 4)

Why it matters: code has to be **maintainable, understandable and adaptable**. Good practices give higher code quality, easier teamwork, and future-proofing.

### 4.1 Meaningful names
- Use descriptive names; avoid `x`, `temp`, `data`, `value`, `func1`.
- **Nouns for variables** (`studentName`), **verbs for functions** (`calculateTotal()`).
- Use **camelCase** and be consistent across the codebase.
- Prefer clarity over brevity: `getUserData()` not `getUsrDat()`.
- If a name needs a comment to explain it, the name is wrong.
- Validate inputs and fail early, e.g. throw `IllegalArgumentException` for a negative factorial input.

### 4.2 Comments and documentation
- Comment **why**, not **what**.
- Document edge cases ("handles division by zero").
- Don't over-comment.
- Add **Javadoc** for classes and public methods: purpose, `@param`, `@return`, exceptions.
- Use inline comments only for complex logic.
- Keep **version history** (Git commit messages, changelogs).

### 4.3 SOLID principles
| Principle | Rule | Lecture example |
|---|---|---|
| **S**ingle Responsibility | A class has one reason to change | Split `Customer` into `Customer`, `CustomerDataStorage`, `EmailSender` |
| **O**pen/Closed | Open for extension, closed for modification | `Shape` interface; add `Triangle` without editing existing code |
| **L**iskov Substitution | A subclass must be usable anywhere its parent is | Every `Shape` implementation can be swapped in |
| **I**nterface Segregation | Don't force clients to depend on methods they don't use | Split `Document` into `Printer`, `Scanner`, `FaxMachine` |
| **D**ependency Inversion | High- and low-level modules depend on abstractions | `NotificationService` depends on a `Notifier` interface, injected through the constructor |

### 4.4 DRY: Don't Repeat Yourself
"Every piece of knowledge or logic must have a single, unambiguous representation within a system." Put shared logic in one place.

### 4.5 Error handling and exception management
1. **Use meaningful error messages** that are clear, concise and have context.
2. **Throw exceptions only for exceptional situations**, not for normal control flow.
3. **Catch specific exceptions**, not the general `Exception`.
4. **Fail fast**: detect errors as early as possible.
5. **Graceful degradation**: one failing feature shouldn't bring down the system.
6. **User-friendly feedback**: no raw stack traces for end users.
7. **Centralised error handling**: one consistent mechanism for handling and logging.
8. **Test exceptions**: write unit tests for the error paths.

### 4.6 Performance and optimisation
1. Optimise only when needed; don't optimise early.
2. Choose efficient algorithms.
3. Avoid unnecessary work such as redundant loops or queries; cache results.
4. Use proper data structures (sets, maps).
5. Keep memory use low; avoid leaks; pool objects where it helps.
6. **Batch operations**: fewer DB and I/O calls.
7. **Lazy loading**: load only when needed.
8. **Concurrency**: thread pools and async work for heavy tasks.
9. **Optimise DB queries**: proper **indexing**, fewer expensive joins and subqueries.
10. **Caching**: keep frequently used data in memory.
11. **Reduce network calls**: compress, cache, and use efficient protocols.

### 4.7 Security best practices
1. **Strong authentication**, including **MFA**.
2. **Encryption** of data at rest and in transit (**SSL/TLS**).
3. **Input validation** to prevent SQL injection and XSS; use **parameterised queries**.
4. **Least privilege** for users *and* systems.
5. **Secure password storage**: hash and salt with a strong algorithm (e.g. BCrypt).
6. **Safe error handling**: generic messages for users, detailed logs for developers.
7. **Security testing**: regular **vulnerability scanning** and **penetration testing**.
8. **API security**: proper authentication and authorisation with **OAuth 2.0** or **API keys**.

---

## 5. Design Patterns (Lecture 5)

**Definition:** standardised, proven, reusable solutions to recurring design problems.
**Benefits:** reuse, maintainability, consistency, efficient problem-solving, shared vocabulary.

| Category | Purpose | Examples |
|---|---|---|
| **Creational** | How objects are created (WHAT, WHO, HOW) | **Singleton**, **Factory Method**, Abstract Factory, Builder, Prototype |
| **Structural** | How classes and objects fit together | **Adapter**, Bridge, Composite, Decorator, Facade |
| **Behavioural** | How objects talk and share responsibility | **Observer**, **Strategy**, **Command**, Iterator, State |

**Patterns covered in detail:**
- **Singleton**: one instance with global access, e.g. a shared DB connection pool. Private constructor, `getInstance()`.
- **Factory Method**: subclasses decide which class to create. Creation is kept separate from use.
- **Adapter**: bridges incompatible interfaces, e.g. old XML system to new JSON system, without changing either side.
- **Observer**: one-to-many notifications (subject → observers), e.g. weather station displays or newsletters. Loose coupling and dynamic subscription.
- **Strategy**: interchangeable algorithms behind one interface.
- **Command**: wraps a request as an object, which enables queueing, logging and undo.

**Pitfalls to avoid:** over-engineering, extra complexity, a learning curve, using a pattern only because it is familiar, performance overhead. No pattern fits every problem.

> First identify the *category* of the problem, then pick the pattern.

---

## 6. Web Application Security (Lecture 6.1)

### Stakes and objectives
- **Stakes:** data protection, service availability, compliance (GDPR, HIPAA, and similar).
- **CIA triad:** **Confidentiality** (only authorised access), **Integrity** (accurate, trustworthy data), **Availability** (service is up when needed).

### Authentication vs Authorisation
- **Authentication** = *who you are* (the passport).
- **Authorisation** = *what you may do* (the boarding pass).

### Authentication
- **Credentials:** something you *know* (password), *have* (token or phone), or *are* (biometrics).
- **Methods:** single-factor → **2FA** → **MFA** (strongest).
- **Challenges:** password fatigue, phishing, credential stuffing.

### Basic authentication
- Base64 `username:password` in the `Authorization` header. This is **encoding, not encryption**.
- **Must be used over HTTPS.** Only suitable for low-risk internal tools or quick API testing, **not production**.
- Limits: no sessions, no logout, credentials can be sniffed without TLS, awkward for users.

### JSON Web Tokens (JWT)
- **Header** (type and algorithm, e.g. HS256 or RSA), **Payload** (registered, public and private claims), **Signature** (proves the token is authentic).
- Advantages: **compact**, **self-contained** (fewer DB lookups), **cross-domain/platform**.

---

## 7. OAuth 2.0 (Lecture 6.2)

- An open **authorisation** framework for **delegated authorisation**: third parties get access without the user's password.
- **Four roles:** Resource Owner (user), Client (app), Authorisation Server (issues tokens), Resource Server (hosts the data).
- **Grant types:** authorisation code, implicit, password, client credentials.
- **Real uses:** **SSO** ("Log in with Google"), third-party access, **securing microservice-to-microservice communication**.

### Authorisation Code flow (PhotoPrint ↔ SocialPix example)
1. **Register** the client and get a **client ID + client secret**.
2. Redirect the user to the auth server with the client ID and **scope** (least privilege, e.g. read-only).
3. The user logs in and consents, and the server returns an **authorisation code**.
4. The client exchanges the code + client ID + secret **on the server side** (the secret is never exposed to the browser).
5. It receives an **access token + refresh token**.
6. It calls the resource server with `Authorization: Bearer <access token>`.
7. When the access token expires, it uses the **refresh token** to get a new one without asking the user again.

---

## 8. Master Checklist for an Enterprise Project

Use this list to self-assess any EAD project.

**Architecture and design**
- [ ] Chosen architecture fits the problem (microservices, event-driven, layered, and so on) and the reason is written down
- [ ] Clear layered structure inside each component (controller → service → repository)
- [ ] Loose coupling through well-defined APIs; services don't share databases
- [ ] An API gateway / BFF in front of the services
- [ ] Asynchronous event-driven communication where real-time or decoupling is needed (broker, pub-sub)
- [ ] Design patterns used where they solve a real problem, without over-engineering

**Code quality**
- [ ] Meaningful names, camelCase, consistent conventions
- [ ] Javadoc on public classes and methods; comments explain *why*
- [ ] SOLID followed (small focused classes, interfaces, constructor injection)
- [ ] DRY: shared contracts and utilities live in one place
- [ ] No dead or placeholder code

**Error handling**
- [ ] Centralised exception handler (`@RestControllerAdvice`)
- [ ] Specific exception types; no blanket `catch (Exception)`
- [ ] Fail fast (input validation at the boundary, no unsafe config defaults)
- [ ] User-friendly error responses, no stack traces
- [ ] Graceful degradation (retries, dead-letter queues, fallbacks)
- [ ] Unit tests for error paths

**Performance**
- [ ] DB indexes, pagination, batching
- [ ] Caching for hot data
- [ ] Async and concurrent processing where useful
- [ ] Compression and fewer network calls

**Security**
- [ ] Strong authentication (JWT/OAuth2, ideally MFA)
- [ ] Role-based authorisation (least privilege)
- [ ] Passwords hashed with BCrypt or Argon2
- [ ] TLS everywhere; secrets never in Git
- [ ] Input validation and parameterised queries
- [ ] Generic user-facing error messages
- [ ] Automated vulnerability scanning and security testing
- [ ] Least privilege for *systems* too (non-root containers, network policies)

**Deployment and operations**
- [ ] Every service containerised (Docker, slim, immutable, versioned images)
- [ ] Deployed on Kubernetes (replicas, autoscaling, probes, resource limits)
- [ ] Load balancing
- [ ] CI/CD pipeline (build, test, deploy)
- [ ] Health checks and monitoring

---

# PART B: SwiftDrop Compliance Review

## How the review was done

**Scope:** the full `BitCraft_SwiftDrop` monorepo: 10 Spring Boot backend services, `common-contracts`, the React frontend, Docker, Helm (AKS) and GitHub Actions.

**Method (read-only):**
- Read `README.md`, the `AGENTS.md` files, the ADR, docs, Dockerfiles, `docker-compose.yml`, the Helm chart, CI/CD workflows, `.gitignore`, and the frontend auth and nginx files.
- **The Java source files are nested too deeply to copy out of the project folder, and the on-device shell was still setting up during the review.** So the backend code was checked through the project's **own compiled JARs** (`target/*.jar.original`, built 13–15 Nov 2026). They hold every class, annotation, exception table, string constant, `application*.yml` and Flyway migration. All 619 classes were disassembled with `javap` and scanned.
- Test inventories came from `surefire-reports` and `failsafe-reports`. Coverage came from each module's `target/site/jacoco/jacoco.csv`.

**Limitation:** comments and Javadoc are removed at compile time, so **comment and documentation quality inside `.java` files could not be checked.** If a JAR is older than your latest source edits, a few findings may already be fixed.

**Legend:** ✅ Meets the standard · ⚠️ Partly meets / needs improvement · ❌ Gap

---

## Overall verdict

SwiftDrop is a **very strong** enterprise project. It meets the module's roadmap goals: Spring Boot backend, Docker, Kubernetes, JWT/OAuth2 and inter-component communication. It goes beyond them with an API gateway, event-driven RabbitMQ messaging with dead-letter queues, a separate database per service, Flyway, OpenAPI, Testcontainers and CI/CD to AKS.

Most gaps are **hardening and consistency** issues, not design flaws. The most important ones are:
- **Containers run as root.** 9 of 10 service images, and their Kubernetes pods, run as root.
- **No automated vulnerability scanning** in CI.
- **Refresh tokens sit in browser storage, and the frontend has no Content-Security-Policy.**
- **Fail-open JWT defaults** in the production config.
- **Placeholder classes** and **uneven test coverage**.

---

## Scorecard

### 1. Enterprise characteristics (Lecture 1)

| Characteristic | Status | Evidence in SwiftDrop |
|---|---|---|
| Scalability | ✅ | Independently deployable services; Helm `HorizontalPodAutoscaler` per service; `replicas: 2` defaults; Redis-backed rate limits |
| Integration | ✅ | Merchant B2B APIs + API keys, outbound webhooks with HMAC signatures, Google OAuth, Azure Communication email, LLM client |
| Security | ⚠️ | Strong JWT/RBAC/BCrypt/rate limiting (see §6). Gaps: root containers, no scanning, token storage (see the gap list) |
| Performance | ✅ | 85 `CREATE INDEX` statements across 46 Flyway migrations; `Pageable` endpoints; Redis caching; bulk CSV ingestion |
| Reliability | ✅ | Dead-letter exchange `swiftdrop.dlx.exchange`, retry policy (`ListenerRetryPolicyConfig`), idempotent consumers (`ProcessedEvent` tables), after-commit publishing (`AfterCommitRabbitRelay`), startup/readiness/liveness probes |

### 2. Course roadmap deliverables (Lecture 1)

| Deliverable | Status | Evidence |
|---|---|---|
| Spring Boot backend | ✅ | Java 25, Spring Boot 3.5, Maven reactor with 11 modules |
| Docker | ✅ | Multi-stage Dockerfile per service, `docker-compose.yml` with 9 Postgres + RabbitMQ + Redis |
| Kubernetes | ✅ | Helm chart `infra/helm/swiftdrop` (namespaces, StatefulSets, Deployments, HPA, NetworkPolicy, Ingress); AKS CD pipeline |
| JWT / OAuth2 | ✅ | RS256 JWTs signed by `auth-iam-service`, published through a **JWKS endpoint** (`JwksController`), checked at the gateway (`JwksKeyStore`); Google OAuth 2.0 auth-code flow (`GoogleOAuthService`, `OAuthCallbackPage`) |
| Inter-component communication | ✅ | REST through Spring Cloud Gateway + RabbitMQ topic exchange `swiftdrop.events.exchange` |
| BFF / API gateway | ✅ | `api-gateway` is the only browser entry point (`/api/v1/...`); internal routes are blocked (`InternalRouteBlockFilter`) |
| Framework choice | ✅ | Mature, widely used frameworks (Spring, React, Vite, Tailwind) |

### 3. Architecture (Lecture 2)

| Standard | Status | Evidence |
|---|---|---|
| Microservices with clear boundaries | ✅ | 9 business services + gateway, each owning one bounded context (README, `AGENTS.md`) |
| Database per service (loose coupling) | ✅ | 9 physically separate PostgreSQL databases; cross-service FKs forbidden (`.agents/rules/infra-db-isolation.md`, ADR-001) |
| Event-driven architecture | ✅ | Producers (`*EventPublisher`), consumers (`*Consumer`), a broker, pub-sub routing keys, async handling, DLQs: every EDA concept in the lecture is present |
| EDA weaknesses addressed (ordering, consistency) | ✅ | Idempotency tables, `eventId`/`correlationId` preserved, after-commit publishing, `DefinitionsJsonParityTest` |
| Layered architecture inside each service | ✅ | `controller/ → service/ → domain/repository/` with `dto/`, `exception/`, `config/`, `messaging/` in every service |
| Client-server | ✅ | React SPA → gateway → services |
| Architecture decisions documented | ✅ | `docs/architecture/ADR-001`, Mermaid diagrams, broker topology doc |
| Containers over VMs; immutable, versioned images | ✅ | Images tagged with the commit SHA in CD; only changed components rebuild |
| Load balancing | ✅ | Traefik ingress + Kubernetes Services across replicas |

### 4. Coding best practices (Lecture 3 and 4)

| Standard | Status | Evidence |
|---|---|---|
| Meaningful names, camelCase, conventions | ✅ | e.g. `TrackingNumberGenerator`, `CourierMatchingEngine`, `calculate...`/`validate...` style; consistent package layout enforced by `backend/AGENTS.md` |
| Comments and Javadoc | ❓ Not verifiable | Javadoc is removed from compiled classes. Project-level docs are excellent (README, ADR, design docs, member guides, OpenAPI specs) |
| Version history | ✅ | Git with a `dev` branch flow; PR review notes in `docs/design/m7/pr*-review.md` |
| SRP | ⚠️ | Mostly small, focused services. **Oversized classes:** `OrderPdfGeneratorService` (~52 KB source), `AuthController` (~18 KB), `JwtAuthenticationGlobalFilter`, `GoogleOAuthService` (~19 KB) |
| OCP | ✅ | `PricingStrategy` with `Standard`/`Express`/`SameDay` implementations; delivery state handlers |
| LSP | ✅ | Interchangeable implementations: `LlmClient` → `OpenAiCompatibleLlmClient`/`MockLlmClient`; `EmailSender` implementations |
| ISP | ✅ | Small interfaces (`PricingClient`, `EmailSender`, `DeliveryStateHandler`, `LlmClient`) |
| DIP / dependency injection | ✅ | Spring DI with services depending on interfaces such as `PricingClient`, `LlmClient` and `EmailSender`. A handful of classes use `@Autowired` (e.g. `ProofOfDeliveryController`, `ContactClient`); check that it sits on constructors, not fields |
| DRY | ✅ | `common-contracts` holds shared events, DTOs (`ApiResponse<T>`), enums and security headers; frontend types are **generated** from OpenAPI, not hand-copied |
| No dead code | ❌ | 8 placeholder classes ship in production builds (see gap G5) |

### 5. Error handling (Lecture 4)

| Standard | Status | Evidence |
|---|---|---|
| Centralised error handling | ✅ | A `@RestControllerAdvice GlobalExceptionHandler` in **every** service + `GatewayGlobalExceptionHandler` |
| Meaningful, specific exception types | ✅ | e.g. `InvalidStateTransitionException`, `TooManyLoginAttemptsException`, `PricingUnavailableException`, `OrderNotFoundException` |
| Catch specific exceptions | ⚠️ | ~20 classes still use a broad `catch (Exception)` or `catch (RuntimeException)` (see gap G6) |
| Fail fast | ⚠️ | Jakarta `@Valid` at controller boundaries in every service. But production config falls back to insecure defaults instead of failing (gap G4) |
| Graceful degradation | ✅ | `LocalPricingFallback`, DLQs, retry/backoff (`ExponentialBackoffScheduler`), `MockLlmClient` fallback |
| User-friendly feedback | ✅ | Standard `ApiResponse` envelope; `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`; frontend `apiErrorMessage.ts` |
| Tests for error scenarios | ✅ | `PricingErrorHandlingTest`, `ConsumerFailureHandlingIT`, `CourierShiftErrorHandlingIT`, `CreateOrderValidationTest` |

### 6. Performance (Lecture 4)

| Standard | Status | Evidence |
|---|---|---|
| DB indexing | ✅ | 85 index statements across the migrations |
| Pagination / batch operations | ✅ | `Pageable` in order, dispatch, merchant and IAM; bulk CSV ingestion in merchant |
| Caching | ✅ | Redis for the JWT blacklist, rate limits, OTP and API-key validation cache (`api-key-cache-ttl-seconds`), JWKS cache |
| Concurrency / async | ✅ | Async messaging; `ConcurrentAssignmentIT` and `DispatchPersistenceConcurrencyIT` test races |
| Fewer network calls | ✅ | The gateway caches API-key validation and JWKS; live tracking uses SSE/WebSocket push instead of polling |
| Schema safety | ✅ | `ddl-auto: validate` in all 18 profiles; Flyway owns the schema |

### 7. Security (Lecture 4, 6.1 and 6.2)

| Standard | Status | Evidence |
|---|---|---|
| Authentication vs authorisation separated | ✅ | AuthN in `auth-iam-service` + gateway JWT filter; AuthZ through roles, `@PreAuthorize`/`@EnableMethodSecurity`, `RoleGuard.tsx`, `DeliveryAccessPolicy` |
| JWT done properly | ✅ | RS256 with a JWKS endpoint, `jti` blacklist in Redis, issuer/audience claims, refresh-token rotation (`RefreshTokenService`), RS256 tests |
| OAuth 2.0 | ✅ | Google authorisation-code flow; the code is exchanged on the server side (`OAuthExchangeRequest`) so the client secret stays on the server |
| Strong authentication / MFA | ⚠️ | Email OTP for verification and password reset, password policy, login lockout (`LoginAttemptService`). **No second factor at login**, even for admin or dispatcher roles (gap G11) |
| Secure password storage | ✅ | BCrypt `PasswordEncoder` in `SecurityConfig` |
| Input validation / SQL injection | ✅ | Jakarta validation + `ValidationPatterns`; Spring Data JPA (parameterised queries) |
| TLS in transit | ✅ | Ingress TLS enabled by default (`ingress.tls.enabled: true`) with a cert-manager `ClusterIssuer` |
| Secrets not in Git | ✅ | `.env`, `values-secrets*.yaml`, `*.pem` are git-ignored; CD writes secrets from GitHub Secrets to a temp file and deletes it afterwards; Azure login uses **OIDC** (no stored cloud passwords) |
| Least privilege (users) | ✅ | Five roles; internal endpoints blocked at the gateway; NetworkPolicy `allowFromServices` |
| Least privilege (systems/containers) | ❌ | Only the gateway image runs as a non-root user (gap G1) |
| API security | ✅ | JWT for users, **API keys** for merchants (`MerchantApiKeyGlobalFilter`), HMAC-signed outbound webhooks, rate limiting on public tracking |
| Safe error messages | ✅ | Central handlers, plus PII masking (`TrackingDataSanitizerProxy`, `TrackingContactMasker`, `MaskingTest`, `TemplatePiiTest`) |
| Browser hardening | ⚠️ | nginx sends `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`. **No `Content-Security-Policy`** (gap G3) |
| Security testing / vulnerability scanning | ❌ | Security *unit* tests exist (`RbacIntegrationTest`, `CourierHorizontalAuthTest`, `TrackingScrapingTest`). **No dependency or image vulnerability scanning** in CI (gap G2) |
| Dev shortcuts kept out of production | ✅ | "Continue in development mode" is behind `import.meta.env.DEV` |

### 8. Design patterns (Lecture 5)

| Pattern | Status | Where SwiftDrop uses it |
|---|---|---|
| Singleton | ✅ | All Spring beans (one instance per container), e.g. `JwtKeyProvider` |
| Factory Method | ✅ | `@Configuration` bean factories: `EmailSenderConfig` picks the email sender, `PricingClientConfig`, `WebClientConfig` |
| Builder | ✅ | Lombok `@Builder` on entities and DTOs (e.g. `CustomerOrder$CustomerOrderBuilder`) |
| Adapter | ✅ | `HttpPricingClient` adapts the pricing REST API to the `PricingClient` interface; `OpenAiCompatibleLlmClient`; `MerchantOrderNormalizer` maps merchant formats to internal orders |
| Proxy | ✅ | `TrackingDataSanitizerProxy` strips PII before public broadcast |
| Observer / Pub-Sub | ✅ | RabbitMQ publishers and consumers; gateway broadcast hub to SSE/WebSocket subscribers |
| Strategy | ✅ | `PricingStrategy` → `StandardPricingStrategy`, `ExpressPricingStrategy`, `SameDayPricingStrategy` (with a class diagram in `docs/design/m4`) |
| State | ✅ | Delivery FSM: `DeliveryStateMachine` + `CreatedState`, `AssignedState`, `OutForDeliveryState`, ...; `AssignmentStateMachine` in dispatch |
| Template | ✅ | `NotificationTemplate` + `TemplateRendererService` |
| Over-engineering avoided | ⚠️ | Generally justified. The empty `StateMachineConfig` and stub guards add complexity without behaviour (G5) |

### 9. Testing and CI/CD

| Standard | Status | Evidence |
|---|---|---|
| Unit + integration tests | ✅ | 125+ test classes across the backend (from surefire/failsafe reports); Testcontainers ITs (dispatch failsafe suite) |
| CI on every PR | ✅ | `ci-backend.yml` (compile, unit, integration, JUnit report, JaCoCo upload), `ci-frontend.yml`, `ci-helm.yml` (lint, template, schema checks) |
| CD | ✅ | `cd-aks-deploy.yml`: detects changed components, builds and pushes to ACR, `helm upgrade` with a `production` environment gate |
| Coverage gate | ❌ | JaCoCo only runs `prepare-agent` + `report`, with no `check` minimum, so coverage can fall without failing the build (G7) |
| Frontend tests | ❌ | No test runner (Vitest/Jest) or test files; `npm run lint` only runs `tsc` type-checking, not ESLint (G8) |

**Local JaCoCo line / branch coverage per module** (from `target/site/jacoco/jacoco.csv`; some reports date from ~5 Nov, and newer PR reports in `docs/test-reports/` show higher numbers for pricing and merchant):

| Module | Line | Branch |
|---|---:|---:|
| dispatch-assignment-service | 85.4% | 69.6% |
| api-gateway | 83.7% | 67.2% |
| analytics-ai-service | 81.8% | 55.4% |
| customer-order-service | 80.8% | 59.0% |
| pricing-zone-service | 78.7% | 58.3% |
| notification-service | 66.7% | 46.6% |
| merchant-b2b-service | 56.0% | 44.1% |
| delivery-execution-service | 51.7% | **25.6%** |
| auth-iam-service | **50.2%** | 44.3% |
| webhook-resiliency-service | **45.0%** | **23.0%** |
| common-contracts | **42.3%** | 28.6% |

---

## Gaps and recommended fixes (by priority)

### 🔴 High priority

**G1: Containers run as root (least-privilege principle, Lecture 4)**
- **Found:** Only `backend/api-gateway/Dockerfile` creates a `swiftdrop` user (UID 1001), sets `USER`, adds a `HEALTHCHECK`, and tunes the JVM. The other **9 service Dockerfiles** (auth-iam, customer-order, pricing-zone, dispatch, delivery, merchant, webhook, notification, analytics) end with only `ENTRYPOINT ["java","-jar","/app/app.jar"]`, so they **run as root**. In `templates/backend-services.yaml` only the *wait-for-dependencies init container* has `runAsNonRoot: true`; the **main app container has no `securityContext`**.
- **Fix:** Copy the gateway's runtime stage into all nine Dockerfiles (`addgroup/adduser`, `USER swiftdrop`, `HEALTHCHECK`, `-XX:MaxRAMPercentage=75.0`). In the Helm app container add:
  ```yaml
  securityContext:
    runAsNonRoot: true
    runAsUser: 1001
    allowPrivilegeEscalation: false
    readOnlyRootFilesystem: true   # mount an emptyDir at /tmp
    capabilities: { drop: ["ALL"] }
  ```
  Do the same for the frontend nginx container (use `nginxinc/nginx-unprivileged`).

**G2: No vulnerability scanning (security testing, Lecture 4)**
- **Found:** No Dependabot/Renovate config, no CodeQL, no OWASP Dependency-Check, no Trivy image scan in any workflow.
- **Fix:** Add `.github/dependabot.yml` (Maven, npm, Docker, GitHub Actions); add a Trivy step after `docker build` in `cd-aks-deploy.yml` that fails on HIGH/CRITICAL; optionally add CodeQL for Java and TypeScript.

**G3: Refresh token in browser storage with no CSP (XSS → account takeover)**
- **Found:** `frontend/src/store/useAuthStore.ts` saves `token` **and `refreshToken`** in `sessionStorage` (`partialize`). `frontend/nginx.conf` has good headers but **no `Content-Security-Policy`**. Any XSS bug could read the 30-day refresh token.
- **Fix:** At least add a strict CSP to nginx (`default-src 'self'; script-src 'self'; connect-src 'self' wss:; frame-ancestors 'none'; object-src 'none'`). Better, move the refresh token to an **HttpOnly, Secure, SameSite=Strict cookie** set by the gateway, and keep only the short-lived access token in memory.

### 🟠 Medium priority

**G4: Production config "fails open" instead of "fails fast" (Lecture 4)**
- **Found:** `application-aks.yml` in `auth-iam-service` and `api-gateway` has `algorithm: ${JWT_ALGORITHM:HS256}` and `secret: ${JWT_SECRET:swiftdrop-local-dev-secret-key-...}`. All 9 business services' AKS profiles default `DB_PASSWORD`/`RABBITMQ_PASSWORD` to `changeme_*`. Helm sets `JWT_ALGORITHM: RS256` and real secrets today, but if a variable goes missing, production would quietly start with a publicly known signing key.
- **Fix:** Remove the defaults from the `aks` profiles (`${JWT_SECRET}` with no fallback) so the app refuses to start. Also add a startup check that rejects HS256 or the dev secret when the `aks` profile is active.

**G5: Placeholder / dead code shipped in production builds**
- **Found (compiled classes with no real logic):**
  - `iam/service/AuthIamService` (returns a status string), `iam/controller/AuthIamController`, `iam/dto/AuthIamRequest`
  - `order/service/OrderLifecycleService` (returns a status string)
  - `pricing/service/GeofenceResolverService` (returns `"ready"`)
  - `notification/messaging/consumer/LifecycleNotificationConsumer` (empty `process()`)
  - `merchant/messaging/publisher/BulkOrderIngestionPublisher` (empty `process()`)
  - `delivery/config/StateMachineConfig` (empty class)
  - `delivery/statemachine/guards/GeofenceProximityGuard`: **`isSatisfied()` always returns `true`**. If it is ever wired into a transition, the proximity check is silently bypassed.
- **Fix:** Delete unused scaffolding, or implement it and add tests. Never leave a guard that always passes.

**G6: Broad `catch (Exception)` blocks ("catch specific exceptions", Lecture 4)**
- **Found in ~20 classes**, e.g. `OrderPdfGeneratorService` (4), `MerchantOrderIngestController` (2), `ContactClient` (3), `OutboundWebhookDispatcher` (2), `InboundEventDecoder` (2), `JwksKeyStore` (2), `AiChatService`, `ProofOfDeliveryStorageService`; and broad `catch (RuntimeException)` in `TokenIssuanceService` (5), `OtpService` (4), `LoginAttemptService` (3), dispatch consumers.
- **Fix:** Catch the concrete types (`IOException`, `JsonProcessingException`, `RestClientException`, `DataAccessException`, `JwtException`, ...). Where a broad catch is deliberate (e.g. a message consumer that must route poison messages to the DLQ, or a Redis outage fallback), keep it narrow, log it with context, and add a one-line comment saying why.

**G7: Uneven test coverage and no coverage gate**
- **Found:** Security-critical modules have the lowest coverage: `auth-iam-service` 50% line, `delivery-execution-service` 26% branch (the FSM + PoD), `webhook-resiliency-service` 45%/23%, `common-contracts` 42%. The JaCoCo plugin in `backend/pom.xml` has no `check` goal.
- **Fix:** Add a `jacoco:check` execution (e.g. `LINE COVEREDRATIO ≥ 0.70`, `BRANCH ≥ 0.50` per module) so CI fails when coverage drops. Prioritise tests for the delivery state transitions, OTP and refresh flows, and webhook retry/DLQ replay.

**G8: Frontend has no automated tests or linter**
- **Found:** `frontend/package.json` has no Vitest/Jest/Testing Library and no ESLint. `lint` runs only `tsc --noEmit`. There are no `*.test.ts(x)` files.
- **Fix:** Add Vitest + React Testing Library (start with `RoleGuard`, `apiClient` token refresh, booking `validation.ts`, `passwordPolicy.ts`), and add ESLint with `eslint-plugin-react-hooks` and `jsx-a11y`. Run both in `ci-frontend.yml`.

**G9: A controller returns a JPA entity (layered architecture)**
- **Found:** `PricingQuoteController` returns `ApiResponse<List<PricingZone>>`, where `PricingZone` is a `domain.entity`. This breaks the project's own rule in `backend/AGENTS.md` ("Controllers ... never JPA entities directly") and leaks the persistence model into the API.
- **Fix:** Add a `PricingZoneResponse` record DTO and map to it in the service layer.

### 🟡 Low priority / polish

**G10: SRP: split very large classes**
- `OrderPdfGeneratorService` (~52 KB) → split into layout, sections (header/items/fees/QR), and a font/asset loader.
- `AuthController` (~18 KB) → split into `LoginController`, `RegistrationController`, `PasswordController`, `OAuthController`.
- `JwtAuthenticationGlobalFilter` and `GoogleOAuthService` → pull out token parsing, claim mapping and HTTP calls into helpers.

**G11: MFA for privileged roles (Lecture 4 and 6.1)**
- SwiftDrop already has an OTP pipeline (`OtpService`, `AuthOtpPublisher`). Reuse it as a **second login factor** for `SYSTEM_ADMIN` and `DISPATCHER` (email OTP or TOTP). This meets the lecture's "use MFA" guidance at little cost.

**G12: Documentation drift**
- `docs/design/m2/jwt-lifecycle-spec.md` says the refresh token lasts **7 days** and is kept in **`localStorage`**. The code uses **30 days** (`JWT_REFRESH_TOKEN_EXPIRATION_DAYS:30`) and **`sessionStorage`**. Update the spec, and consider cutting refresh lifetime to 7–14 days with rotation.

**G13: Comments and Javadoc (could not verify)**
- Javadoc is removed at compile time, so check it by hand: every public service method and controller endpoint should have a short Javadoc (purpose, params, return, exceptions). Comments should explain *why*, especially in the FSM guards, the matching engine and the pricing strategies. The OpenAPI `@Operation`/`@Schema` annotations already cover the REST surface well.

---

## Summary table

| Lecture area | Result |
|---|---|
| L1: Enterprise characteristics and roadmap | ✅ All 5 characteristics and all roadmap deliverables met |
| L2: Containers and architecture | ✅ Microservices + EDA + layered + client-server, well documented; ⚠️ container hardening (G1) |
| L3: Naming, comments, SOLID | ✅ Strong SOLID/DRY; ⚠️ a few oversized classes (G10), dead stubs (G5); ❓ Javadoc not verifiable |
| L4: Error handling, performance, security | ✅ Central handlers, indexing, caching, BCrypt, TLS; ⚠️ broad catches (G6), fail-open config (G4); ❌ no vuln scanning (G2) |
| L5: Design patterns | ✅ Singleton, Factory, Builder, Adapter, Proxy, Observer, Strategy, State, Template, all used for real reasons |
| L6.1: Web security, JWT | ✅ RS256 + JWKS + blacklist + rotation; ⚠️ token storage / CSP (G3); ⚠️ no login MFA (G11) |
| L6.2: OAuth 2.0 | ✅ Google auth-code flow with the code exchanged on the server |
| Testing and CI/CD | ✅ Thorough CI/CD; ❌ no coverage gate (G7), no frontend tests (G8) |

**Suggested order of work:** G1 → G2 → G3 → G4 → G5 → G9 → G7 → G8 → G6 → G10–G13.

---

# PART C: Implementation Status (branch `shehan/chore/ead-standards-hardening`)

Every gap from Part B was worked on. Each row says what changed and where; "Follow-up" lists what is deliberately left for later and why.

| Gap | Status | What changed |
|---|---|---|
| **G1** Root containers | ✅ Done | All 9 service Dockerfiles now match the gateway: `swiftdrop` user (UID 1001), `USER`, `HEALTHCHECK`, ZGC + `MaxRAMPercentage`. Helm pods: `runAsNonRoot`, seccomp `RuntimeDefault`, `allowPrivilegeEscalation: false`, all capabilities dropped, **read-only root filesystem** with an `emptyDir` at `/tmp` (`podSecurity` in `values.yaml`). Frontend uses `nginx-unprivileged` on 8080. PostgreSQL/RabbitMQ/Redis also drop all capabilities. |
| **G2** Vulnerability scanning | ✅ Done | `.github/workflows/security-scan.yml` (CodeQL for Java + TypeScript, Trivy dependency and Dockerfile/Helm misconfiguration scans; on PRs, dev/main pushes and weekly). `cd-aks-deploy.yml` scans every image with Trivy and blocks the push on a fixable HIGH/CRITICAL CVE. `.github/dependabot.yml` for Maven, npm, Docker base images and Actions. |
| **G3** Token storage / CSP | ✅ Done | Refresh token moved to an **HttpOnly, Secure, SameSite=Strict cookie** (`swiftdrop_refresh`, path `/api/v1/auth`) set by `auth-iam-service` (`RefreshTokenCookies`); it is no longer in the JSON body or in browser storage. Content-Security-Policy added to both nginx configs; the Helm nginx config now also sends the hardening headers (it sent none before) and HSTS when TLS is on. |
| **G4** Fail-open config | ✅ Done | `aks` profiles no longer default DB/RabbitMQ passwords or the dev JWT secret; `JWT_ALGORITHM` defaults to RS256. `JwtKeyProvider` (IAM) and `ProductionJwtGuard` (gateway) refuse to start under `aks` without RS256. |
| **G5** Placeholder code | ✅ Done | Deleted the 9 placeholder classes, including the always-true `GeofenceProximityGuard`. Also found and fixed a similar latent issue: `OutForDeliveryState` now **fails closed** if its proof-of-delivery guard or the delivery id is missing. |
| **G6** Broad catches | ✅ Done | ~45 broad catches narrowed to concrete types (`DataAccessException` for Redis, `JsonProcessingException`, `IOException`, `RestClientException`, `JwtException`, `GeneralSecurityException`, OpenPDF `DocumentException`, zxing `WriterException`, ...). The few deliberate boundaries (poison-message decoder, scheduler loops, per-row batch ingestion, provider fallbacks) keep a broad catch with a comment saying why. Found on the way: `CourierAssignedConsumer` silently swallowed failures (now rejects the message), and the webhook HMAC check used a non-constant-time comparison (now `MessageDigest.isEqual`). The merchant CSV endpoint no longer echoes internal exception messages to clients. |
| **G7** Coverage gate | ✅ Done | `jacoco:check` runs in `verify` (so in CI). Target 70% line / 50% branch; modules below it have a baseline set just under today's coverage in their own `pom.xml` (ratchet: coverage can only go up). New tests: `LoginMfaServiceTest`, `RefreshTokenCookiesTest`, fail-closed FSM test, `aks` HS256 rejection, and the first tests for `webhook-resiliency-service` (retry policy, webhook signer, HMAC). Finding: that service had **no test sources at all**; the 45% in Part B came from a stale build folder. Unit-test baselines today: analytics 49/39, auth 53/46, common-contracts 42/29, delivery 63/55, webhook 10/13 (line/branch %); gateway, order, pricing, dispatch, merchant and notification already meet 70/50. |
| **G8** Frontend tests / lint | ✅ Done | Vitest + React Testing Library (RoleGuard, API-client cookie refresh, booking validation, password policy, MFA type guard). ESLint 9 with typescript-eslint, react-hooks and **jsx-a11y**; fixing its findings repaired 15 unlabeled form controls and several mouse-only controls. Both run in `ci-frontend.yml`. |
| **G9** Entity in API | ✅ Done | `GET /api/v1/pricing/zones` returns `ZoneResponse` records; spec and generated types updated. |
| **G10** Oversized classes | ✅ Done | `AuthController` split into `SessionController`, `RegistrationController`, `PasswordController`, `GoogleOAuthController` (+ `ClientRequests` helper). `GoogleOAuthService` → HTTP calls moved to `GoogleApiClient` (Adapter). `OrderPdfGeneratorService` → formatting moved to `PdfFormat`, barcode/QR to `PdfCodes`. `JwtAuthenticationGlobalFilter` → path rules moved to `GatewayPathPolicy`, which also removes a duplicated API-key path list (DRY). |
| **G11** MFA | ✅ Done (opt-in) | Emailed one-time code as a second factor for `SYSTEM_ADMIN` and `DISPATCHER`: `/login` answers **202** with a challenge, `/login/mfa` completes it (`LoginMfaService`, reuses the OTP pipeline; new `LOGIN_MFA` purpose, email template and Flyway `V7`). Frontend login page has the code step; the seed script prompts for the code. **Off by default** (`AUTH_MFA_ENABLED=false`) because the cluster still uses the mock email sender; turning it on without real email would lock admins out. |
| **G12** Doc drift | ✅ Done | `jwt-lifecycle-spec.md` matches the code; refresh lifetime cut from 30 to 14 days. |
| **G13** Javadoc | ✅ Done for the named areas | "Why" Javadoc for the pricing strategies, the proof-of-delivery guard and FSM state, and all new classes. The matching engine was already documented. |

## Follow-up (not done on purpose)

1. **Turn on MFA** once `NOTIFICATION_EMAIL_PROVIDER=azure` is configured: set `AUTH_MFA_ENABLED: "true"` for `auth-iam-service` in Helm. Google sign-in does not ask for the SwiftDrop code (Google has its own 2-step verification).
2. **Dead-letter queue for `delivery.dispatch-assigned.queue`.** It has no DLX, so a rejected courier assignment is dropped (it used to be dropped silently). Adding a DLX changes the queue's arguments, so existing brokers must delete and recreate the queue; plan it as its own change with `definitions.json`.
3. **First CI run of the scanners** may report existing CVEs or misconfigurations; that is their purpose. Fix them or add a justified entry to `.trivyignore.yaml`.
4. **Local Docker volume:** the `pod_photos` volume created before this change is root-owned. Run `docker volume rm swiftdrop_pod_photos` once (only if it already exists) so the non-root delivery service can write to it.
5. **Raise the coverage baselines** in each module's `pom.xml` as tests are added, until every module meets 70% / 50%.
