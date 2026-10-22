package com.swiftdrop.gateway.filter;

import com.swiftdrop.contracts.security.JwtHeaderConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Global filter that validates JWT tokens on incoming requests and injects
 * verified identity headers ({@code X-User-Id}, {@code X-User-Role}, {@code X-Merchant-Id})
 * into downstream service requests.
 *
 * <p>Public endpoints (auth, health) are whitelisted and skip validation.</p>
 */
@Slf4j
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
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
            "/api/v1/iam/v3/api-docs/",
            "/api/v1/orders/v3/api-docs/",
            "/api/v1/pricing/v3/api-docs/",
            "/api/v1/dispatch/v3/api-docs/",
            "/api/v1/deliveries/v3/api-docs/",
            "/api/v1/merchants/v3/api-docs/",
            "/api/v1/webhooks/v3/api-docs/",
            "/api/v1/notifications/v3/api-docs/",
            "/api/v1/analytics/v3/api-docs/"
    );

    private final SecretKey signingKey;

    public JwtAuthenticationGlobalFilter(@Value("${swiftdrop.jwt.secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip JWT validation for public endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(JwtHeaderConstants.BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(JwtHeaderConstants.BEARER_PREFIX.length());

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String userRole = claims.get("role", String.class);
            String merchantId = claims.get("merchantId", String.class);

            // Strip any externally-supplied identity headers and inject verified ones
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(JwtHeaderConstants.X_USER_ID);
                        headers.remove(JwtHeaderConstants.X_USER_ROLE);
                        headers.remove(JwtHeaderConstants.X_MERCHANT_ID);
                    })
                    .header(JwtHeaderConstants.X_USER_ID, userId)
                    .header(JwtHeaderConstants.X_USER_ROLE, userRole != null ? userRole : "")
                    .header(JwtHeaderConstants.X_MERCHANT_ID, merchantId != null ? merchantId : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100; // Run before route-specific filters
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
}
