# Member 4 - Pricing & Zone Service test and coverage evidence

Execution date: 2026-11-14. Windows, Java 25.0.5.0.3, Maven <fill in: output of `mvn -v`>.
Branch `Sandali/docs/pricing-diagrams-test-evidence`, based on `dev` at
`<fill in: git rev-parse --short origin/dev>`. These are observed results, not a
claim that the branch has been merged or accepted.

## Commands and results

| Check | Observed result |
|---|---|
| `mvn -pl pricing-zone-service -am clean verify` (run from `backend/`) | BUILD SUCCESS, including `spring-boot:repackage` and the JaCoCo `report` goal. 55 tests run in `pricing-zone-service`, zero failures, errors, or skips. |
| Whole module coverage | 89.4% line (252 of 282), 80% branch (40 of 50), 89% instruction (1,257 of 1,406). |
| `service/` package coverage | 96.3% line (130 of 135), 80% branch. |
| `controller/` package coverage | 78.3% line (18 of 23). No branches. |
| `service/strategy/` package coverage | 100% line (9 of 9), 83% branch. |
| `security/` package coverage | 100% line (4 of 4). |

The 70% line-coverage target for `service/` and `controller/` is met. `service/`
went from 80.7% (109 of 135 lines) to 96.3% in this branch. Packages with no or
partial coverage: `config` (0 of 6 lines), `domain.entity` (0 of 3 lines),
`exception` (23 of 30 lines).

## Test classes in this run

| Test class | Tests |
|---|---|
| `PricingEstimateControllerTest` | 9 |
| `PricingQuoteControllerTest` | 3 |
| `TariffRbacTest` | 12 |
| `DynamicPricingEngineTest` | 9 |
| `TariffAdminServiceTest` | 4 |
| `ZoneAdminServiceTest` | 18 (13 added by this branch) |

## Tests added by this branch

| Test class | Added cases | Proves |
|---|---|---|
| `ZoneAdminServiceTest` | 13 | Create keeps an explicit status; list and get return zones and fail with `ZoneNotFoundException` for an unknown code; update rejects a missing zone, applies the new values, keeps the old status when none is sent, changes it when one is sent, and refreshes `updatedAt`; delete rejects a missing zone and removes a zone with no mapped districts; listing mappings; assigning a district rejects an unknown district name, normalizes the name to upper case when creating a mapping, and moves an existing mapping to a new zone. |

## Committed artifacts

- `surefire-reports/` - Surefire output from the passing run.
- `jacoco/index.html` - browsable JaCoCo report (open it locally).
- `jacoco/jacoco.csv` and `jacoco/jacoco.xml` - machine-readable per-class
  coverage used for the numbers above.

The `target/` build outputs are not committed; they are reproducible with the
command above.

## Not yet verified

- **Integration test**: `EstimateEndpointIT` (Testcontainers, listed in the
  guide's Part C) was not part of this run; the run contained unit and
  controller tests only.
- **Runtime behaviour**: the service was not started against a live database or
  RabbitMQ for this evidence run, and no Docker Compose or AKS check was done.
- **Frontend**: `npm run lint` and `npm run build` were not part of this run.