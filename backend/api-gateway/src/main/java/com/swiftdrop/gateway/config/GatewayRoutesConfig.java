package com.swiftdrop.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route definitions for the SwiftDrop API Gateway.
 * Routes are defined here for clarity and type-safety; YAML-based routes
 * in {@code application.yml} can override or supplement these.
 */
@Configuration
public class GatewayRoutesConfig {

    private final GatewayRouteProperties routes;

    public GatewayRoutesConfig(GatewayRouteProperties routes) {
        this.routes = routes;
    }

    @Bean
    public RouteLocator swiftDropRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

                // ─── Auth / IAM ──────────────────────────────────────────
                .route("auth-iam-service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/users/**", "/api/v1/iam/v3/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/iam/v3/api-docs(?<segment>/?.*)", "/v3/api-docs${segment}"))
                        .uri(routes.auth()))

                // ─── Customer Orders ─────────────────────────────────────
                .route("customer-order-service", r -> r
                        .path("/api/v1/orders/**", "/api/v1/orders/v3/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/orders/v3/api-docs(?<segment>/?.*)", "/v3/api-docs${segment}"))
                        .uri(routes.order()))

                // ─── Pricing & Zones ─────────────────────────────────────
                .route("pricing-zone-service", r -> r
                        .path("/api/v1/pricing/**", "/api/v1/zones/**", "/api/v1/pricing/v3/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/pricing/v3/api-docs(?<segment>/?.*)", "/v3/api-docs${segment}"))
                        .uri(routes.pricing()))

                // ─── Dispatch Assignment ─────────────────────────────────
                .route("dispatch-assignment-service", r -> r
                        .path("/api/v1/dispatch/**", "/api/v1/dispatch/v3/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/dispatch/v3/api-docs(?<segment>/?.*)", "/v3/api-docs${segment}"))
                        .uri(routes.dispatch()))

                // ─── Delivery Execution ──────────────────────────────────
                .route("delivery-execution-service", r -> r
                        .path("/api/v1/deliveries/**", "/api/v1/deliveries/v3/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/deliveries/v3/api-docs(?<segment>/?.*)", "/v3/api-docs${segment}"))
                        .uri(routes.delivery()))

                // ─── Merchant B2B ────────────────────────────────────────
                .route("merchant-b2b-service", r -> r
                        .path("/api/v1/merchants/**",
                                "/api/v1/merchant/**",
                                "/api/v1/merchant-order-ingest/**",
                                "/api/v1/api-key-management/**",
                                "/api/v1/merchants/v3/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/merchants/v3/api-docs(?<segment>/?.*)", "/v3/api-docs${segment}"))
                        .uri(routes.merchant()))

                // ─── Webhooks ────────────────────────────────────────────
                .route("webhook-resiliency-service", r -> r
                        .path("/api/v1/webhooks/**", "/api/v1/webhooks/v3/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/webhooks/v3/api-docs(?<segment>/?.*)", "/v3/api-docs${segment}"))
                        .uri(routes.webhook()))

                // ─── Notifications ───────────────────────────────────────
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**", "/api/v1/notifications/v3/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/notifications/v3/api-docs(?<segment>/?.*)", "/v3/api-docs${segment}"))
                        .uri(routes.notification()))

                // ─── Analytics ───────────────────────────────────────────
                .route("analytics-ai-service", r -> r
                        .path("/api/v1/analytics/**",
                                "/api/v1/operational-kpi/**",
                                "/api/v1/ai-delivery-assistant/**",
                                "/api/v1/reports/**",
                                "/api/v1/analytics/v3/api-docs/**")
                        .filters(f -> f.rewritePath("/api/v1/analytics/v3/api-docs(?<segment>/?.*)", "/v3/api-docs${segment}"))
                        .uri(routes.analytics()))

                .build();
    }
}
