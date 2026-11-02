# Member 2 authentication baseline

Verified on 2 November 2026 (Asia/Colombo), against branch `dev`, commit `dbb82a0`.
This records existing behavior. No application fixes or dependency changes were made.

## Environment and scope

Only the existing six Member 2 containers were used: `iam-postgres`, `redis`,
`rabbitmq`, `auth-iam-service`, `api-gateway`, and `frontend`. No other business
services or databases were started.

The local `.env` remains an exact copy of `.env.example`. Maven used the installed
Java 25 and the versions declared in the repository. Frontend checks used the
existing `node:22-alpine` build stage and `npm ci` with the checked-in lockfile.

The SHA-256 of `frontend/package-lock.json` before and after verification was:

```text
792E184E2914EC4819E62F80C887D04B428E11D88ADF7147F3638452358F4F72
```

## Automated checks

| Check | Result |
|---|---|
| Existing IAM unit tests | 24 passed; 0 failures/errors/skips |
| Existing common-contracts test | 1 passed; 0 failures/errors/skips |
| Frontend `npm run lint` | Passed |
| Frontend `npm run build` | Passed; bundle-size warning remains |
| Live API/environment assertions | 46 passed, 3 failed, 1 administrator group not run |

The three failed assertions represent the two defects below. The live totals
include health, persistence-format, fixture-cleanup and lockfile checks; they are
not 50 new committed test cases.

The first Maven attempt used offline mode and stopped because the declared
Surefire 3.5.1 JUnit provider was not cached. Retrying online downloaded the missing
test dependencies without changing their declared versions, and all tests passed.

Command for the backend tests, with `JAVA_HOME` pointing to the installed JDK 25:

```powershell
mvn -B -f backend/pom.xml -pl auth-iam-service -am test --no-transfer-progress
```

Surefire evidence is generated in each tested module's `target/surefire-reports/`.

## Confirmed working behavior

- IAM and gateway health, IAM OpenAPI through the gateway, frontend HTTP response,
  and the frontend-to-gateway proxy returned HTTP 200. Both IAM migrations were
  recorded as successful.
- Customer registration returned 201. Duplicate registration returned 409. Weak
  passwords, invalid emails and attempted staff self-registration returned 400.
- Username and email login returned 200. Incorrect passwords returned 401.
- A valid token retrieved the correct profile through both the gateway and frontend
  proxy. Anonymous, malformed-token and tampered-signature requests returned 401.
- Customers received 403 when listing users, provisioning accounts, changing their
  own role, or changing account status.
- The observed access token used HS256 with the expected subject, role, district,
  issuer, audience, JWT ID and one-hour lifetime. This does not satisfy the planned
  RS256/JWKS deliverable.
- Database inspection confirmed BCrypt strength 12 and hashed refresh-token storage.
  No raw password, token, hash, or test credential is included in this report.
- Normal refresh issued different access and refresh tokens. Logout returned 200,
  invalidated that access token at both IAM and the gateway, and invalidated the
  supplied refresh token.
- Password changes invalidated old access/refresh tokens, rejected the old password,
  and allowed login with the new password. The usability of the new session failed,
  as recorded below.
- Five failed login attempts returned 401; the sixth returned 429.

## Defect 1: refresh replay does not persist full revocation

Reproduction using a dedicated synthetic customer:

1. Login to obtain refresh token A.
2. Refresh A successfully, obtaining replacement B.
3. Reuse A: the endpoint returns 401, as expected.
4. Inspect the user's refresh rows: one remains active; expected zero.
5. Refresh B: the endpoint returns 200; expected 401 after replay revocation.

The observed persisted state confirms that replay does not invalidate the
replacement refresh credential. Source inspection points to unchecked exception
rollback after `revokeAllForUser` in the transactional refresh path. The existing
Mockito tests verify calls but do not verify committed database state.

Fix scope: IAM refresh transaction handling and database-backed regression tests.

## Defect 2: new sessions are blocked after a password change

Reproduction using another dedicated synthetic customer:

1. Login, then change the password successfully.
2. Confirm old credentials/sessions no longer work.
3. Login with the new password: the endpoint returns 200 and issues a new token.
4. Use the new token on `/api/v1/users/me`: the gateway returns 401; expected 200.

The current invalidation writes a blacklist entry for the entire user ID, with a
TTL equal to the configured access-token lifetime. Both old and newly issued
tokens match that entry. The new session is therefore rejected immediately.

Fix scope: make revocation distinguish old sessions from new ones; regression-test
password changes and later role/status transitions using the chosen approach.

## Limits and cleanup

- The database initially had no accounts and no active administrator. Successful
  administrator provisioning, role changes, and deactivation/reactivation were
  not exercised. Negative customer/admin access checks were exercised.
- Frontend compilation and HTTP/proxy behavior were verified. An interactive
  browser sign-in, session restoration, and automatic refresh were not exercised.
- Expired-token timing, simultaneous refreshes, Google login, RSA/JWKS, dispatcher
  district permissions and merchant approval integration are outside this run.
- Three uniquely named synthetic customers were created. Their accounts and
  associated refresh/role rows were removed using only the UUID/name pairs created
  during this invocation. Test-specific Redis entries expire under their existing
  configured TTLs; the cache was not flushed.
- Runtime checks demonstrate the behavior of the current local services. Passing
  unit tests alone do not establish a complete authentication implementation.

## Next work

The baseline is recorded with known failures. Retain this evidence, agree the
identity/session contracts in plan step 2, and carry these two defects into the
session-lifecycle work. Add regression coverage that proves committed revocation
and immediate usability of newly authenticated sessions.
