# Member 3 test evidence (customer-order-service)

Run on `dev` at `5130555` (PR 6 merged) with `mvn -f backend/pom.xml -pl customer-order-service -am verify`, JDK 25, Docker running for Testcontainers.

| Suite | Result |
|---|---|
| Unit tests (surefire) | 186 run, 0 failures, 0 errors, 0 skipped |
| Integration tests (failsafe, `OrderPersistenceAndEventIT`) | 6 run, 0 failures, 0 errors, 0 skipped |
| Build | BUILD SUCCESS |

JaCoCo line coverage (from `jacoco.csv`):

| Package | Lines covered |
|---|---|
| `service` | 93.7% (887/947) |
| `service.pricing` | 100% (27/27) |
| `controller` | 80.7% (46/57) |
| `config` | 100% (42/42) |
| `dto` | 98.8% (81/82) |
| `exception` | 95.1% (58/61) |
| Whole module | 90.0% (1181/1312) |

Files: `surefire/` and `failsafe/` hold the per-class result summaries, `jacoco.csv` holds the per-class coverage numbers.
