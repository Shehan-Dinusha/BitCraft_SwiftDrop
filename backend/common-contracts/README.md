# Shared caller identity

`com.swiftdrop.contracts.security.CallerContext` is the Member 2 identity helper
specified in the [identity/session contract](../../docs/design/m2/identity-and-session-contract.md).
It uses only Java and existing shared enums; there is no servlet, WebFlux or Spring
Security dependency in the implementation.

## Trust boundary

The helper reads identity already verified by the gateway. It does not verify a
JWT, authenticate a merchant API key, or prove where a request came from. A service
must exclude untrusted direct callers or authenticate them separately before
trusting these headers. Consumers must still load the business record and apply
the appropriate guards to its actual owner/district.

Public routes must not use caller-supplied identity headers as verified identity.
Follow `JwtHeaderConstants`; never introduce alternate header names for a service.

## Header contract

| Header | Context field | Accepted value |
|---|---|---|
| `X-User-Id` | `userId` | Required UUID in full hyphenated form |
| `X-User-Role` | `role` | Required canonical `UserRole` enum name |
| `X-Merchant-Id` | `merchantId` | Optional full UUID |
| `X-District-Id` | `districtId` | Optional `SriLankanDistrict` enum name, such as `COLOMBO` |

Outer whitespace is trimmed. UUID letters may be uppercase or lowercase; UUID
groups cannot be shortened. Role/district names are case-sensitive. Persona and
legacy role aliases such as `ADMIN_OPS`, `ADMIN` and `DRIVER` are rejected rather
than promoted to another role. Blank optional headers remain absent; a malformed
nonblank optional header rejects the context instead of removing its scope.

The immutable record requires a non-null user ID and role even when constructed
directly. A context may lack merchant/district scope; trying to use an absent scope
fails its corresponding guard. Missing scope never implies global access.

## Framework adapters

In a servlet controller or service boundary, use the existing request:

```java
CallerContext caller = CallerContext.from(request::getHeader);
```

In a reactive gateway boundary, use the existing request headers:

```java
CallerContext caller = CallerContext.from(exchange.getRequest().getHeaders()::getFirst);
```

Both are adapters to `from(Function<String, String>)`; no servlet-specific overload
or new dependency is needed. This does not install the helper into any controller
or gateway filter automatically.

## Authorization guards

`requireRole` and the scope guards are separate checks. An owner or district match
alone does not grant a courier/dispatcher operation.

```java
// assignedCourierId must come from the loaded delivery, not a client parameter.
caller.requireRole(UserRole.COURIER);
caller.requireSelfOrRole(assignedCourierId);

// This operation explicitly allows a system administrator across districts.
caller.requireRole(UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN);
caller.requireDistrict(resourceDistrict, UserRole.SYSTEM_ADMIN);

// An ordinary merchant needs MERCHANT role and the matching business ID.
caller.requireMerchant(ownerMerchantId);
```

| Guard | Allows |
|---|---|
| `requireRole(roles...)` | Only a role explicitly listed |
| `requireSelfOrRole(owner, roles...)` | The owning user or an explicitly listed role |
| `requireMerchant(merchant, roles...)` | A merchant with matching business identity, or an explicitly listed role |
| `requireDistrict(district, roles...)` | Matching caller district, or an explicitly listed role |

There is no automatic `SYSTEM_ADMIN` bypass. If an operation permits one, its owner
must pass that role explicitly. Empty/null role allowlists grant nothing. Null
resource owner IDs and null resource districts always fail, even with an explicit
privileged role. Services choose their own 404 response for missing/hidden records
before invoking these guards; this helper does not load resources.

## Error mapping when adopting the helper

| Failure | Exception | `statusCode()` |
|---|---|---:|
| Missing or malformed verified identity | `UnauthenticatedCallerException` | 401 |
| Valid caller fails an operation's guard | `ForbiddenException` | 403 |

Both extend `CallerContextException`. Error messages identify the failed header or
rule without echoing raw header values. The shared exception intentionally has no
Spring annotation: every consuming service must map it to its existing
`ApiErrorResponse` before using the helper in a live endpoint. A generic exception
handler alone could otherwise incorrectly turn these failures into HTTP 500.

Example handler in a servlet service that already uses Spring Web:

```java
@ExceptionHandler(CallerContextException.class)
public ResponseEntity<ApiErrorResponse> handleCallerContext(
        CallerContextException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.valueOf(exception.statusCode());
    return ResponseEntity.status(status).body(ApiErrorResponse.of(
            status.value(), status.getReasonPhrase(), exception.getMessage(),
            request.getRequestURI(), request.getHeader(JwtHeaderConstants.X_CORRELATION_ID)));
}
```

The reactive equivalent belongs in the gateway's own error-writing path. Adoption
and endpoint ownership/district enforcement remain with each service owner; they
are not silently enabled by adding this library class.

## Validation

With `JAVA_HOME` set to the installed Java 25 and the declared dependencies cached:

```powershell
mvn -o -B -f backend/pom.xml -pl common-contracts -am test --no-transfer-progress
```

The helper tests require no database, Redis, RabbitMQ or application containers.
They cover canonical headers, invalid/missing identity, role aliases, ownership,
merchant role plus business identity, absent/cross-district scope, explicit
administrator allowances, null resource scope, and 401/403 exception metadata.
