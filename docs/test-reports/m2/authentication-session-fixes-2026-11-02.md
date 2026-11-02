# Member 2 session regression fixes

Date: 2 November 2026 (Asia/Colombo).

This follows `authentication-baseline-2026-11-02.md`. The original baseline is
retained as historical evidence. No dependency versions, Maven POMs, frontend
package files, Dockerfiles, Compose configuration, or environment values changed.

## Implementation

### Refresh replay and expiry

Both `AuthenticationService.refresh` and `RefreshTokenService.rotate` now preserve
their transaction on `InvalidRefreshTokenException` and `AccountNotActiveException`.
This commits intentional replay, expiry, and inactive-account revocations before
returning the rejection. Other failures retain the usual rollback behavior.

The rule is needed on both methods: leaving the caller's transaction unchanged
would still roll back the inner service's security updates.

### Password-change session invalidation

Per-user Redis blacklist values now hold an ISO-8601 revocation cutoff instead of
the boolean marker `1`. IAM and the HTTP gateway share the comparison in
`common-contracts/security/JwtSessionRevocation`.

New access tokens include the signed private claim `sessionIssuedAt`, preserving
the full issuance timestamp. Standard JWT `iat` remains present and unchanged.
Tokens issued at or before the cutoff are revoked; a later login is immediately
usable, including within the same second. The Redis cutoff expires after the
configured access-token lifetime.

Compatibility behavior:

- Existing tokens without the new claim use standard `iat` conservatively.
- Existing boolean blacklist markers remain effective until their existing TTL
  expires. They are not silently discarded.
- Individual token/JWT-ID logout entries remain effective.
- IAM and gateway retain their existing Redis-unavailable behavior.
- HTTP paths, DTO schemas, response formats and frontend types are unchanged.
- Deploy the updated gateway reader before IAM writes the new cutoff format.

No database entity changed, so no Flyway migration was needed.

## Regression tests

Before the fixes, all three new database-backed tests failed:

1. A replayed refresh left one replacement token active in PostgreSQL.
2. A fresh login after changing the password received 401 on profile access.
3. An expired refresh token's revoked flag rolled back after rejection.

After the fixes:

| Suite | Result |
|---|---|
| IAM, including database-backed session regressions | 29 passed |
| Shared contracts, including cutoff compatibility/boundary tests | 6 passed |
| Gateway authentication regression suite | 6 passed |

The new tests cover committed revocation, rejection of the replacement refresh
credential after replay, rejection of old access tokens, immediate usability of
new password logins, fresh logins after replay, expiry revocation, same-second
issuance, standard-iat compatibility, legacy Redis markers and individual logout.

Integration tests use the existing IAM PostgreSQL and Redis. They are opt-in and
do not start Testcontainers or other application containers. They use a separate
Redis prefix and remove only their own uniquely identified account rows and keys.
They deliberately do not wrap test methods in rollback transactions, so assertions
inspect committed state after the failed service call.

With the existing IAM database and Redis running, and `JAVA_HOME` set to Java 25:

```powershell
mvn -o -B -f backend/pom.xml -pl auth-iam-service -am "-Dswiftdrop.integration-tests=true" package --no-transfer-progress
mvn -o -B -f backend/pom.xml -pl api-gateway -am "-Dtest=JwtAuthenticationGlobalFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false" package --no-transfer-progress
```

Offline mode assumes the project's declared dependencies have already been cached.

## Broader gateway suite finding

The complete gateway suite ran 16 tests and reported one unrelated failure:
`TrackingDataSanitizerProxyTest.mapsLegacyDeliveryStates` expects delivery `FAILED`
to map to `RETURNED_TO_SENDER`, while the unchanged `PublicOrderStatus` implementation
maps it to `FAILED`. The mapping, enum and existing test have no changes in this
patch. The complete gateway suite is therefore not reported as green.

## Local runtime verification

After rebuilding IAM and gateway, the baseline and extra HTTP checks against the
gateway, direct IAM endpoint and frontend proxy recorded **52 passes, zero
failures, and one administrator group not run** (no administrator credentials).
The total includes environment, storage-format, fixture-cleanup and lockfile
assertions; it is not a claim of 53 new committed test cases.

Confirmed results:

- Replaying refresh A after rotation to B returns 401, leaves zero active refresh
  rows, and makes B return 401.
- A legitimate fresh login after that replay succeeds and can retrieve its profile.
- Password change rejects the old password, access token and refresh token.
- Login with the new password succeeds, and profile access immediately returns 200
  through both gateway and direct IAM.
- Registration, input validation, role restrictions, normal rotation, logout,
  login throttling, health, OpenAPI and frontend proxy checks continue to pass.
- All three synthetic customers and their refresh/role rows were removed. Live
  test Redis entries are left to their configured expiration; no cache flush ran.

Only the existing six Member 2 containers were used. IAM and gateway were updated
sequentially; the other four containers were retained.

The Docker source build encountered a DNS failure for Maven Central. The local
images were therefore built from the Maven packages that passed the tests, using
the same runtime stages and `eclipse-temurin:25-jre-alpine` image declared by the
repository. No repository Dockerfile or image version was changed.
