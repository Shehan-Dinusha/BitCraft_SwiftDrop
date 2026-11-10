# Member 1 - API Gateway test and coverage evidence

Execution date: 2026-11-10. Windows, Java 25.0.5, Maven 3.9.11. Branch
`Shehan/feat/rs256-jwks-gateway` at `e26b316`, which is `dev` at `bd0ded7` plus
the gateway RS256/JWKS change. These are observed results, not a claim that the
branch has been merged or accepted.

## Commands and results

| Check | Observed result |
|---|---|
| `mvn -f backend/pom.xml -pl api-gateway -am test` | BUILD SUCCESS. `common-contracts` 46/46 and `api-gateway` 68/68, zero failures, errors, or skips. |
| `mvn -f backend/pom.xml -pl api-gateway -am verify` | Test phase passed all 68 gateway cases. The later `spring-boot:repackage` step failed because a local development instance (PID 12388) held `api-gateway-0.1.0-SNAPSHOT.jar` open; this is a file-lock issue, not a test failure. |
| JaCoCo report goal (`org.jacoco:jacoco-maven-plugin:0.8.16:report`) | BUILD SUCCESS. Report generated from the same `jacoco.exec` as the passing run. |
| Whole gateway coverage | 66.1% line (623 of 942), 50.5% branch (190 of 376). |
| `service/` package coverage | 83.3% line (125 of 150): enrichment 100%, sanitizer 95.9%, broadcast hub 66.7%. |
| `controller/` package coverage | 48.2% line (27 of 56). The SSE/HTTP paths are covered; the WebSocket and STOMP session branches are the remaining gap. |

The 70% controller coverage target is not met yet. It is recorded here rather
than hidden; the STOMP session handler is the main untested block.

## RS256/JWKS evidence added by this branch

| Test class | Cases | Proves |
|---|---|---|
| `JwtAuthenticationGlobalFilterTest` | 7 | HS256 happy path, forged identity header stripping, public-path sanitization, API-key bypass, expired token, wrong issuer, wrong audience. |
| `JwtAuthenticationGlobalFilterRs256Test` | 4 | RS256 token accepted through a JWKS lookup, unknown `kid` rejected, HS256 token rejected while RS256 is configured, token signed by an untrusted RSA key rejected. |
| `JwksKeyStoreTest` | 6 | Fetch and cache, throttled refetch for unknown `kid`, cooldown expiry, `cached()` before/after fetch, unsupported JWK entries skipped, malformed document rejected. |
| `TrackingTokenVerifierTest` | 5 | Missing/malformed token, HS256 principal extraction, wrong issuer/audience, public/private topic authorization. |
| `TrackingTokenVerifierRs256Test` | 4 | Cached RS256 key accepted, uncached key rejected, wrong issuer/audience rejected, HS256 token rejected while RS256 is configured. |

## Committed artifacts

- `surefire-reports/*.txt` - 17 suite summaries from the passing gateway run.
- `jacoco/index.html` - browsable JaCoCo report (open it locally).
- `jacoco/jacoco.csv` - machine-readable per-class coverage used for the numbers
  above.

The XML surefire files and the `target/` build outputs are intentionally not
committed; they are reproducible with the commands above.

## Not yet verified

- **RS256 end-to-end with a live IAM instance**: not run. `dev` still sets
  `JWT_ALGORITHM=HS256` in IAM. Flipping IAM to RS256 is the agreed separate
  follow-up PR after this gateway change is merged.
- **Docker Compose / AKS runtime**: not exercised in this run. The evidence
  covers unit and controller tests plus coverage, not a live cluster.
- **Frontend live tracking demo**: not re-run here; the existing `dev` demo flow
  was not modified by this branch.

No tokens, passwords, private keys, or connection strings are stored in this
folder.
