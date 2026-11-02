# Member 2 CallerContext verification

Date: 2 November 2026 (Asia/Colombo).
Branch: `Nimsara/feat/IAM-Service`.

## Delivered

The shared module now provides an immutable `CallerContext` record with public
user ID, canonical backend role, optional merchant ID and optional district.
`from(Function<String, String>)` adapts servlet or reactive headers without a new
framework dependency.

Guards cover explicit roles, self-or-privileged access, merchant ownership, and
district scope. Administrators have no automatic bypass. Missing optional scope
does not widen access, and missing resource owners/districts are denied.
Merchant ownership also requires the ordinary caller to have `MERCHANT` role.

Missing/malformed identity throws `UnauthenticatedCallerException` (401 metadata).
Denied authorization throws `ForbiddenException` (403 metadata). Both derive from
`CallerContextException`; consumers must map them into their existing HTTP error
envelope before adopting the helper in an endpoint.

The [usage guide](../../../backend/common-contracts/README.md) documents adapters,
guard composition, the trust boundary, and exception mapping. The existing
step-2 contract draft is retained and updated to point to the implementation.

## Validation results

| Check | Result |
|---|---|
| New `CallerContextTest` cases, including parameterized inputs | 40 passed |
| All shared-contract tests, including existing status/revocation tests | 46 passed; no failures/errors/skips |
| Install tested shared contracts into the local Maven repository | Passed; existing artifact version retained |
| Compile gateway and IAM with the updated shared module | Passed |

Test coverage includes required header absence/whitespace, strict UUID parsing
(including shortened groups), canonical roles, rejected persona/legacy aliases,
malformed optional district/merchant headers, safe error messages, absent scope,
cross-user/business/district denial, explicit administrator allowances, null
allowlists, null resource scope and role/scope guard composition.

Commands used with installed Java 25 and cached repository dependencies:

```powershell
mvn -o -B -f backend/pom.xml -pl common-contracts -am test --no-transfer-progress
mvn -o -B -f backend/pom.xml -pl common-contracts -am -DskipTests install --no-transfer-progress
mvn -o -B -f backend/pom.xml -pl api-gateway,auth-iam-service -am -DskipTests compile --no-transfer-progress
```

Surefire evidence is under `backend/common-contracts/target/surefire-reports/`.
Tests were not repeated during artifact installation or compatibility compilation.

## Scope and preserved constraints

- No dependency or plugin versions, POM files, frontend package files, environment
  files, Compose configuration or container images changed.
- No containers were started, stopped, rebuilt or accessed for these checks.
- `frontend/package-lock.json` SHA-256 remains
  `792E184E2914EC4819E62F80C887D04B428E11D88ADF7147F3638452358F4F72`.
- No controller, public DTO, entity, event or gateway route changed. No migration,
  OpenAPI change or frontend-type regeneration was required.
- No business-service authorization behavior is claimed to have changed: adoption
  belongs to each service owner. This is library verification and consumer
  compilation, not an HTTP/runtime authorization test.
- The existing uncommitted step-2 document was preserved. No commit or push was
  performed during this task.
