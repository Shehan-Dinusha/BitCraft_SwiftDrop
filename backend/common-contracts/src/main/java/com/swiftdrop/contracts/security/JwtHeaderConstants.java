package com.swiftdrop.contracts.security;

/**
 * HTTP header constants injected by the API Gateway after JWT validation.
 * Downstream services read these headers to identify the authenticated principal
 * without re-parsing the JWT.
 *
 * <p>These headers are stripped from external requests by the gateway's
 * {@code JwtAuthenticationGlobalFilter} and re-injected with verified claims.</p>
 */
public final class JwtHeaderConstants {

    private JwtHeaderConstants() {
        // Utility class – no instantiation
    }

    /** UUID of the authenticated user. */
    public static final String X_USER_ID = "X-User-Id";

    /** Role of the authenticated user (maps to {@link com.swiftdrop.contracts.enums.PersonaRole}). */
    public static final String X_USER_ROLE = "X-User-Role";

    /** UUID of the merchant (present only for MERCHANT persona). */
    public static final String X_MERCHANT_ID = "X-Merchant-Id";

    /** Distributed tracing correlation ID propagated across service boundaries. */
    public static final String X_CORRELATION_ID = "X-Correlation-Id";

    /** Authorization header carrying the Bearer JWT token. */
    public static final String AUTHORIZATION = "Authorization";

    /** Bearer token prefix. */
    public static final String BEARER_PREFIX = "Bearer ";
}
