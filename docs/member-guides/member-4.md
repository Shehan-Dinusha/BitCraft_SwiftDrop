# Member 4: Sandali · Pricing & Zone Management

> Draft guide, written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`).
> Your assignment list is the source of truth for **what** you build. The repo is the source of truth for **how** (stack, folders, ports, names).

## 0. Your scope at a glance

| Item | Value |
|---|---|
| Backend module | `backend/pricing-zone-service` |
| Local app port | `8083` |
| Database | `pricing-postgres` container, host port `5434`, database `swiftdrop_pricing` |
| Frontend folders you own | `frontend/src/features/admin-ops/pages/ZonePricingEditorPage.tsx`, new `frontend/src/features/pricing/` (`PriceEstimatePreview.tsx`), new `src/services/api/pricingApi.ts` |
| Documentation deliverable | Class diagram of the Pricing Strategy Pattern + Zone entity ERD sub-model |
| Branch prefix | `feature/m4-<short-topic>` (off `dev`, PR back into `dev`) |

Read [README.md](README.md) in this folder once. It has the shared-file ownership map and the list of cross-member contracts.

## 1. What already exists on `dev`

This is the thinnest service (about 560 lines), so you have the most freedom.

- `PricingQuoteController` at `/api/v1/pricing`: `POST /quote`, `GET /zones`. `GeofenceZoneController` has only `/health`.
- `DynamicPricingEngine.calculateQuote(...)`: one class with all rules (`VOLUMETRIC_DIVISOR = 5000`, rule version `PRICING-2026-V1`). **No Strategy pattern yet.**
- `GeofenceResolverService`, entities `PricingZone`, `DistrictZoneMapping`, `TariffRule`, `SurgeMultiplier`; Flyway V1, V2.
- Tests: `PricingQuoteControllerTest`, `DynamicPricingEngineTest`.
- No RabbitMQ (you don't need it). No security checks.
- Frontend: `ZonePricingEditorPage.tsx` exists; there is no `pricingApi.ts`.
- Enums you will use: `OrderSpeed` (`STANDARD, EXPRESS, SAME_DAY`), `ZoneTier`, `SriLankanDistrict` in `common-contracts`.

## 2. Gaps to close

- [ ] **Strategy Pattern** with three strategies: `StandardPricingStrategy` (flat base + weight-tier multiplier), `ExpressPricingStrategy` (zone-crossing fee + express surcharge), `BulkyPricingStrategy` (volumetric weight from L×W×H/5000).
- [ ] **`POST /api/v1/pricing/estimate`** (the name in your assignment). Keep `/quote` as an alias so nothing breaks.
- [ ] **Zone CRUD**: create/update/delete zones, map postal codes and cities to zones.
- [ ] **Tariff management**: edit base rates, tier multipliers, surcharges (versioned).
- [ ] **RBAC**: only `DISPATCHER`/`SYSTEM_ADMIN` may change zones or tariffs (read `X-User-Role`); estimate stays open to any logged-in user.
- [ ] **One source of pricing truth**: Member 3 has a copy of your engine (`PricingCalculationService`). Give them a stable contract so they can delete it.
- [ ] Frontend: estimate preview component for Member 3's wizard + admin zone/tariff editor.

## 3. Boundaries

**You own:** `backend/pricing-zone-service/`; enums `OrderSpeed`, `ZoneTier`, `SriLankanDistrict` (ask before renaming values, other services use them); the frontend files above.

**You do not touch:** `customer-order-service` (Member 3 replaces their copy themselves); the booking wizard (you only provide the component).

## 4. Who you depend on

Almost nobody: you can work fully alone.

| Member | What | Note |
|---|---|---|
| Member 3 (Dulanga) | Consumer of your `/estimate` contract and `<PriceEstimatePreview>` | Agree the request/response JSON in your **first** week and freeze it |
| Member 10 (Buddika) | Calls `/estimate` from the AI tool `calculate_estimate(weight, destination)` | Same contract; make `destination` accept a city name |
| Member 1 (Shehan) | Gateway route already covers `/api/v1/pricing/**` and `/api/v1/zones/**` | Ask only if you add a new prefix |

## Part A. One-time setup (about 1 hour)

**A1. Install tools.** Java 25 (Temurin), Maven 3.9+, Node.js 20+, Docker Desktop, Git, VS Code with *Extension Pack for Java* and *Spring Boot Extension Pack*. Check them:

```powershell
java -version; mvn -version; node --version; npm --version; docker --version; docker compose version
```

**A2. Clone and branch from `dev`** (never work on `main`):

```powershell
git clone https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop.git
cd BitCraft_SwiftDrop
git checkout dev
git pull origin dev
git checkout -b feature/m4-setup
```

**A3. Environment file.**

```powershell
Copy-Item .env.example .env
```

Never commit `.env`. Every new variable you add goes into `.env.example`, `docker-compose.yml` and `infra/k8s/services/pricing-zone-service.yml` (rule from `AGENTS.md`).

> **Known setup traps (skip this box once `repo-fix-plan.md` is merged on `dev`):**
> 1. `docker-compose.yml` starts Postgres with password `changeme_pg`, but services default to `swiftdrop`.
> 2. Compose creates the RabbitMQ user `swiftdrop`/`changeme_rabbit`, but most services set no RabbitMQ user, so Spring falls back to `guest`/`guest` and cannot connect.
>
> In each terminal where you run your service, set these first:
>
> ```powershell
> $env:DB_PASSWORD = 'changeme_pg'
> $env:SPRING_RABBITMQ_USERNAME = 'swiftdrop'
> $env:SPRING_RABBITMQ_PASSWORD = 'changeme_rabbit'
> ```
>
> Then, in your first PR, add `username: ${RABBITMQ_USERNAME:swiftdrop}` and `password: ${RABBITMQ_PASSWORD:changeme_rabbit}` under `spring.rabbitmq` in **your** `application.yml`.

**A4. Install the shared contracts jar** (repeat whenever `backend/common-contracts` changes on `dev`):

```powershell
mvn -f backend\pom.xml -pl common-contracts -am -DskipTests install
```

**A5. Install frontend packages.**

```powershell
cd frontend; npm install; cd ..
```

**A6. Start only the infrastructure you need.**

```powershell
docker compose up -d pricing-postgres
docker compose ps
```

RabbitMQ UI: http://localhost:15672 (user `swiftdrop`, password `changeme_rabbit`).

**A7. Run your service and confirm it boots.**

```powershell
mvn -f backend\pom.xml -pl pricing-zone-service -am spring-boot:run
```

Open http://localhost:8083/actuator/health and http://localhost:8083/swagger-ui.html.

**A8. Record today's baseline.** Run your module's tests once and write down what passes and fails before you change anything:

```powershell
mvn -f backend\pom.xml -pl pricing-zone-service -am test
```

**A9. Read before coding:** `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `docs/LOCAL-DEVELOPMENT.md`, `frontend/COMPONENTS.md`, and `.agents/rules/*.md`. They define package layout, DTO rules (Java records + `@Schema`), Flyway rules (new `V<N>__*.sql`, never edit an old one), and the Velocity design-system rules.

## Part B0. How you work every day (same for all members)

1. Start from fresh `dev`: `git checkout dev; git pull origin dev; git checkout -b feature/m4-<topic>`.
2. Work stage by stage (Part B): **one stage = one commit**, one group of stages = one PR. Small commits are easy to review and to revert.
3. Commit messages follow the style already on `dev`: `feat(<scope>): ...`, `fix(<scope>): ...`, `test(<scope>): ...`, `docs(<scope>): ...`.
4. Only edit files you own (section "Boundaries"). If you need a change in someone else's file, message that member with the exact change, or open a tiny PR and ask them to review it.
5. Before pushing: compile and test your module, and if you touched the frontend run `npm run lint` and `npm run build` in `frontend/`.
6. Open the PR into `dev`, ask at least one teammate to review, and merge only when it builds.
7. After `common-contracts` changes land on `dev`, everyone re-runs step A4.

## Part B. Stage-by-stage implementation plan

Each **stage** below is one small, reviewable commit. Finish a stage, check its *Done when* line, then commit with the suggested message. Stages are grouped into **PRs**: push your branch after every stage, and open the PR when the group is done.

Rules:
- One stage = one commit. Don't mix unrelated changes (e.g. a refactor and a new endpoint) in one commit.
- Commit only when the code compiles and the stage's check passes. Never commit broken code to save work; use `git stash` instead.
- If a stage grows past ~300 changed lines, split it and commit the first half.
- Tests live in the same stage as the code they test, unless the stage is explicitly a test stage.
- Branch per PR: `feature/m4-<pr-topic>` from fresh `dev`.

> **Before you start:** `repo-fix-plan.md` (credentials and gateway URLs) is being done separately. Pull `dev` once it's merged. You need nobody else to start.


### PR 1: Contract first


#### Stage 1: Freeze the estimate contract

**Objective:** Members 3 and 10 can build against a stable API.

**Steps:**

1. Write `docs/api-specs/pricing-estimate.md` with the request/response JSON from section 2 (pickup/drop-off city, weight, dimensions, speed → total, strategy, breakdown, zones, ruleVersion).
2. Share it with Members 3 and 10 and get a thumbs-up.

**Commit:** `docs(pricing): define estimate API contract`

**Done when:** Members 3 and 10 confirm in chat.


#### Stage 2: Estimate DTOs and endpoint stub

**Objective:** Endpoint exists with the agreed shape.

**Steps:**

1. Add `EstimateRequest`/`EstimateResponse` records with `@Schema` and Jakarta validation.
2. `POST /api/v1/pricing/estimate` delegating to the current `DynamicPricingEngine`; keep `/quote` working.

**Commit:** `feat(pricing): add estimate endpoint with agreed contract`

**Done when:** Swagger shows `/estimate`; a sample request returns a price.


### PR 2: Strategy Pattern


#### Stage 3: Strategy interface

**Objective:** Define the pattern.

**Steps:**

1. Add `PricingContext` (weight, dimensions, zones, speed), `PriceBreakdown`, and `PricingStrategy` interface with `supports(ctx)` and `calculate(ctx)`.

**Commit:** `refactor(pricing): introduce PricingStrategy interface`

**Done when:** Compiles; no behaviour change.


#### Stage 4: StandardPricingStrategy

**Objective:** Flat base + weight-tier multiplier.

**Steps:**

1. Implement it; read base rate and tiers from `TariffRule` repository.
2. `StandardPricingStrategyTest` (parameterised: 0.5, 1.0, 3.0, 3.01, 50 kg; rounding HALF_UP to 2 dp).

**Commit:** `feat(pricing): add standard pricing strategy`

**Done when:** Tests pass.


#### Stage 5: ExpressPricingStrategy

**Objective:** Zone-crossing fee + express surcharge.

**Steps:**

1. Implement; same-zone vs cross-zone; surcharge applied once.
2. `ExpressPricingStrategyTest`.

**Commit:** `feat(pricing): add express pricing strategy`

**Done when:** Tests pass.


#### Stage 6: BulkyPricingStrategy

**Objective:** Volumetric weight from dimensions.

**Steps:**

1. Volumetric = L×W×H/5000; charge on max(actual, volumetric); reject zero/negative dimensions.
2. `BulkyPricingStrategyTest`.

**Commit:** `feat(pricing): add bulky volumetric pricing strategy`

**Done when:** Tests pass.


#### Stage 7: Strategy resolver

**Objective:** Pick the right strategy automatically.

**Steps:**

1. `PricingStrategyResolver`: EXPRESS speed → express; volumetric > actual × 1.5 → bulky; else standard. Document the rule.
2. `PricingStrategyResolverTest`.

**Commit:** `feat(pricing): resolve pricing strategy from parcel and speed`

**Done when:** Tests pass.


#### Stage 8: Engine uses strategies

**Objective:** Replace the monolithic calculation.

**Steps:**

1. `DynamicPricingEngine` becomes: resolve zones → resolve strategy → calculate; delete the old inline maths; bump `ruleVersion` to `PRICING-2026-V2`.
2. Update `DynamicPricingEngineTest`.

**Commit:** `refactor(pricing): delegate engine to pricing strategies`

**Done when:** All pricing tests pass; the documented example price matches.


### PR 3: Zones and tariffs data


#### Stage 9: Postal-code mapping table

**Objective:** Map postal codes and cities to zones.

**Steps:**

1. Flyway `V3__zone_postal_codes.sql`: `zone_postal_codes(id, zone_id, postal_code UNIQUE, city)` + entity + repository.

**Commit:** `feat(pricing): add zone postal code mapping`

**Done when:** Service boots and Flyway applies V3.


#### Stage 10: Seed tariffs

**Objective:** Prices come from data, not constants.

**Steps:**

1. Flyway `V4__seed_tariffs.sql`: base rates, weight tiers, express surcharge, zone-crossing fees, sample cities (Colombo, Kandy, Galle, Jaffna…).

**Commit:** `feat(pricing): seed default tariffs and zones`

**Done when:** Estimate for 2 kg Colombo → Kandy returns a non-zero total.


#### Stage 11: Estimate integration test

**Objective:** Prove DB-driven pricing end to end.

**Steps:**

1. `EstimateEndpointIT` (Testcontainers Postgres) asserting the documented example.

**Commit:** `test(pricing): add estimate endpoint integration test`

**Done when:** IT passes.


### PR 4: Admin CRUD with RBAC


#### Stage 12: Zone CRUD endpoints

**Objective:** Admins manage zones.

**Steps:**

1. `ZoneAdminController` under `/api/v1/zones`: list/get/create/update/delete zones and postal mappings; validation for unique postal codes.

**Commit:** `feat(pricing): add zone management endpoints`

**Done when:** CRUD works in Swagger.


#### Stage 13: Tariff endpoints

**Objective:** Admins edit multipliers and rates.

**Steps:**

1. `TariffAdminController` under `/api/v1/pricing/tariffs`: list/update; reject negative rates and overlapping weight tiers.

**Commit:** `feat(pricing): add tariff management endpoints`

**Done when:** Updating a tier changes the next estimate.


#### Stage 14: Admin-only writes

**Objective:** Only DISPATCHER/SYSTEM_ADMIN modify tariffs and zones.

**Steps:**

1. Guard every write with `CallerContext.requireRole(DISPATCHER, SYSTEM_ADMIN)` (or `@RequestHeader` until it exists).
2. `TariffRbacTest`: CUSTOMER/COURIER → 403; DISPATCHER/SYSTEM_ADMIN → 200.

**Commit:** `feat(pricing): restrict zone and tariff changes to admins`

**Done when:** Test passes.


### PR 5: Frontend


#### Stage 15: Pricing API client

**Objective:** Typed calls.

**Steps:**

1. Create `services/api/pricingApi.ts` using generated types (`npm run api:sync:specs`).

**Commit:** `feat(frontend): add pricing API client`

**Done when:** `npm run lint` passes.


#### Stage 16: PriceEstimatePreview component

**Objective:** Embeddable price preview for the booking form.

**Steps:**

1. `features/pricing/PriceEstimatePreview.tsx` with props `{ request, onResult? }`; debounce 400 ms; total + breakdown; loading/error; `tabular-nums`.
2. Send Member 3 the import path and props.

**Commit:** `feat(frontend): add price estimate preview component`

**Done when:** Rendered on a test page, it updates as inputs change.


#### Stage 17: Zone editor

**Objective:** Admin configures zones.

**Steps:**

1. `ZonePricingEditorPage` at `/ops/zones`: zones grid + postal mapping editor (compact density).

**Commit:** `feat(frontend): add zone management screen`

**Done when:** Create/edit/delete a zone from the UI.


#### Stage 18: Tariff editor

**Objective:** Admin edits multipliers.

**Steps:**

1. Tariff table editor with confirm dialog on save.

**Commit:** `feat(frontend): add tariff editor`

**Done when:** Editing a tariff changes the preview price.


### PR 6: Documentation


#### Stage 19: Diagrams

**Objective:** Your assigned deliverable.

**Steps:**

1. Strategy Pattern class diagram and zone ERD sub-model (Part D) in `docs/design/m4/`.

**Commit:** `docs(pricing): add strategy class diagram and zone ERD`

**Done when:** Files committed and shared.

## Part C. Testing and security (summary)

Every test below is written and committed inside a stage in Part B. Use this table as a final checklist.

| Test | Type | What it proves |
|---|---|---|
| `StandardPricingStrategyTest` | Unit, parameterised | Tier boundaries (0.0, 0.5, 1.0, 3.0, 3.01, 50 kg), rounding |
| `ExpressPricingStrategyTest` | Unit | Same-zone vs cross-zone fee, surcharge applied once |
| `BulkyPricingStrategyTest` | Unit | Volumetric > actual picks volumetric; zero/negative dimensions rejected |
| `PricingStrategyResolverTest` | Unit | Right strategy for each input |
| `TariffRbacTest` | MockMvc | `CUSTOMER`/`COURIER` → 403 on PUT/POST/DELETE zones and tariffs; `DISPATCHER`/`SYSTEM_ADMIN` → 200 |
| `EstimateEndpointIT` | Integration (Testcontainers Postgres) | Seeded tariffs produce the documented example price |

## Part D. Documentation deliverable and demo

1. *Class diagram:* `PricingStrategy` interface, three strategies, `PricingStrategyResolver`, `DynamicPricingEngine`, `PricingContext`, `PriceBreakdown`.
2. *Zone ERD sub-model:* `pricing_zones`, `district_zone_mappings`, `zone_postal_codes`, `tariff_rules`, `surge_multiplier` with keys.

**Your demo part:** in the booking form, change weight and destination and watch the estimate update; as admin, change a tariff and show the new estimate; show a customer token getting 403 on tariff edit.

## Part E. Finishing up

**E1. Definition of done (tick every box before you call your part finished).**

- [ ] Every item in your "Gaps to close" checklist is done or has a written reason why not.
- [ ] `mvn -f backend\pom.xml -pl pricing-zone-service -am verify` passes locally.
- [ ] Coverage report exists for your module (once Member 1 adds JaCoCo: `backend/pricing-zone-service/target/site/jacoco/index.html`). Aim for **70%+ line coverage** on `service/` and `controller/`.
- [ ] Swagger docs for your endpoints are complete (`@Tag`, `@Operation`, `@Schema`) and `docs/api-specs` is updated.
- [ ] Frontend types regenerated with `npm run api:sync:specs` (commit only **your** service's generated file).
- [ ] `npm run lint` and `npm run build` pass in `frontend/`.
- [ ] DB and RabbitMQ credentials in `application.yml` come from env variables (no hard-coded values).
- [ ] No secrets in code, no personal data (phone, address, names) in logs or error messages.
- [ ] Your service block in `docker-compose.yml` starts and is healthy (`docker compose up -d pricing-zone-service`).
- [ ] `infra/k8s/services/pricing-zone-service.yml` has every env variable your `application.yml` reads (bonus marks).
- [ ] Documentation deliverable exported (PNG/PDF + source) to `docs/design/m4/` and sent to whoever compiles the final design PDF.

**E2. Test evidence for the submission.** Save `backend/pricing-zone-service/target/surefire-reports/` and the JaCoCo `index.html` (as a screenshot or PDF) into `docs/test-reports/m4/`.

**E3. Demo preparation.** Rehearse your part of the demo script (Part D) twice on a clean `docker compose down -v` start. Be ready to explain your code, your diagrams, and one security decision you made.

**E4. Contribution sheet line.** Send the team lead a 3-line summary: service, frontend screens, tests and diagrams you delivered, with links to your merged PRs.
