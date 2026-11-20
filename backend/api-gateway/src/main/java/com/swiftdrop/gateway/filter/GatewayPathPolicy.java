package com.swiftdrop.gateway.filter;

import java.util.List;

/**
 * Which gateway paths skip JWT authentication. One place for the rule, shared by the JWT and the
 * merchant API-key filters, so the two can never disagree about which routes use API keys.
 */
final class GatewayPathPolicy {

    static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/username-availability",
            "/api/v1/auth/oauth2/",
            "/api/v1/auth/.well-known/",
            "/api/v1/public/track",
            "/api/v1/public/tracking",
            "/api/v1/tracking",
            "/ws/tracking",
            "/actuator/health",
            "/actuator/health/readiness",
            "/actuator/health/liveness",
            "/swagger-ui.html",
            "/swagger-ui",
            "/swagger-ui/",
            "/v3/api-docs",
            "/webjars",
            "/webjars/",
            "/api/v1/iam/v3/api-docs",
            "/api/v1/iam/v3/api-docs/",
            "/api/v1/orders/v3/api-docs",
            "/api/v1/orders/v3/api-docs/",
            "/api/v1/pricing/v3/api-docs",
            "/api/v1/pricing/v3/api-docs/",
            "/api/v1/dispatch/v3/api-docs",
            "/api/v1/dispatch/v3/api-docs/",
            "/api/v1/deliveries/v3/api-docs",
            "/api/v1/deliveries/v3/api-docs/",
            "/api/v1/merchants/v3/api-docs",
            "/api/v1/merchants/v3/api-docs/",
            "/api/v1/webhooks/v3/api-docs",
            "/api/v1/webhooks/v3/api-docs/",
            "/api/v1/notifications/v3/api-docs",
            "/api/v1/notifications/v3/api-docs/",
            "/api/v1/analytics/v3/api-docs",
            "/api/v1/analytics/v3/api-docs/"
    );

    static final List<String> API_KEY_PATHS = List.of(
            "/api/v1/merchant-order-ingest/",
            "/api/v1/b2b/"
    );

    private GatewayPathPolicy() {
    }

    /** Auth, public tracking, documentation and health endpoints: no JWT required. */
    static boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /** Merchant B2B routes authenticated by API key instead of a JWT. */
    static boolean isApiKeyPath(String path) {
        return API_KEY_PATHS.stream().anyMatch(path::startsWith);
    }
}
