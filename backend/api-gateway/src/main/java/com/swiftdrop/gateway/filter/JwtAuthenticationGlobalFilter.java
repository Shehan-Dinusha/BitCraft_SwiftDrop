package com.swiftdrop.gateway.filter;

import com.swiftdrop.contracts.enums.UserRole;
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
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Global filter that validates JWT tokens on incoming requests and injects
 * verified identity headers ({@code X-User-Id}, {@code X-User-Role},
 * {@code X-Merchant-Id}, {@code X-District-Id}) into downstream service requests.
 *
 * <p>Public endpoints (auth, public tracking, merchant API-key ingestion, health)
 * are whitelisted and skip JWT validation. Blacklisted token IDs, token hashes,
 * and user IDs are checked against Redis; Redis failures fail open for local
 * development availability and are logged for operators.</p>
 */
@Slf4j
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/public/track",
            "/api/v1/public/tracking",
            "/api/v1/tracking",
            "/ws/tracking",
            "/api/v1/merchant/",
            "/api/v1/merchant-order-ingest/",
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

    private static final List<String> API_KEY_PATHS = List.of(
            "/api/v1/merchant/",
            "/api/v1/merchant-order-ingest/"
    );

    private final SecretKey signingKey;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayErrorWriter errorWriter;
    private final String blacklistPrefix;

    public JwtAuthenticationGlobalFilter(
            @Value("${swiftdrop.jwt.secret}") String jwtSecret,
            @Value("${swiftdrop.jwt.blacklist-prefix:jwt:blacklist:}") String blacklistPrefix,
            ReactiveStringRedisTemplate redisTemplate,
            GatewayErrorWriter errorWriter) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.blacklistPrefix = blacklistPrefix;
        this.redisTemplate = redisTemplate;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            if (isApiKeyPath(path)) {
                return chain.filter(exchange);
            }
            ServerHttpRequest sanitized = exchange.getRequest().mutate()
                    .headers(this::stripIdentityHeaders)
                    .build();
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(JwtHeaderConstants.BEARER_PREFIX)) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(JwtHeaderConstants.BEARER_PREFIX.length());

        final Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("JWT validation failed for path {}: {}", path, exception.getMessage());
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        String userId = claims.getSubject();
        String userRole = claimAsString(claims, "role");
        String merchantId = claimAsString(claims, "merchantId");
        String districtId = firstNonBlank(
                claimAsString(claims, "districtId"),
                claimAsString(claims, "district")
        );
        String jwtId = claims.getId();

        return isBlacklisted(token, userId, jwtId)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        log.warn("Rejected blacklisted token for path: {}", path);
                        return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Token is no longer valid");
                    }

                    UserRole canonicalRole = UserRole.fromClaim(userRole);
                    String resolvedRole = canonicalRole != null
                            ? canonicalRole.name()
                            : (userRole == null ? "" : userRole);

                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                stripIdentityHeaders(headers);
                                headers.remove(JwtHeaderConstants.X_API_KEY);
                                headers.remove(JwtHeaderConstants.X_MERCHANT_API_KEY);
                            })
                            .header(JwtHeaderConstants.X_USER_ID, userId == null ? "" : userId)
                            .header(JwtHeaderConstants.X_USER_ROLE, resolvedRole)
                            .header(JwtHeaderConstants.X_MERCHANT_ID, merchantId == null ? "" : merchantId)
                            .headers(headers -> {
                                if (districtId != null) {
                                    headers.set(JwtHeaderConstants.X_DISTRICT_ID, districtId);
                                }
                            })
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private Mono<Boolean> isBlacklisted(String token, String userId, String jwtId) {
        String tokenHash = sha256(token);
        List<String> keys = new ArrayList<>(3);
        keys.add(blacklistPrefix + "token:" + tokenHash);
        if (userId != null && !userId.isBlank()) {
            keys.add(blacklistPrefix + "user:" + userId);
        }
        if (jwtId != null && !jwtId.isBlank()) {
            keys.add(blacklistPrefix + "jti:" + jwtId);
        }

        return Flux.fromIterable(keys)
                .flatMap(redisTemplate::hasKey)
                .any(Boolean::booleanValue)
                .onErrorResume(exception -> {
                    log.warn("Redis blacklist check failed; allowing token: {}", exception.getMessage());
                    return Mono.just(false);
                });
    }

    private void stripIdentityHeaders(HttpHeaders headers) {
        headers.remove(JwtHeaderConstants.X_USER_ID);
        headers.remove(JwtHeaderConstants.X_USER_ROLE);
        headers.remove(JwtHeaderConstants.X_MERCHANT_ID);
        headers.remove(JwtHeaderConstants.X_DISTRICT_ID);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isApiKeyPath(String path) {
        return API_KEY_PATHS.stream().anyMatch(path::startsWith);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    private String claimAsString(Claims claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
