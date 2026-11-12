package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.gateway.security.JwksKeyStore;
import com.swiftdrop.gateway.security.JwsHeader;
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
import reactor.core.scheduler.Schedulers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Global filter that validates JWT tokens on incoming requests and injects
 * verified identity headers ({@code X-User-Id}, {@code X-User-Role},
 * {@code X-Merchant-Id}, {@code X-District-Id}) into downstream service requests.
 *
 * <p>Only auth, public tracking, documentation, and health endpoints are
 * whitelisted. The /api/v1/ai/** chat route requires JWT authentication and
 * receives only gateway-verified identity headers. Merchant API-key paths are handled exclusively by
 * {@link MerchantApiKeyGlobalFilter} and therefore bypass JWT validation.
 * Blacklisted token IDs, token hashes, and user IDs are checked against Redis;
 * Redis failures fail open for local development availability and are logged
 * for operators.</p>
 *
 * <p>Signature verification supports HS256 (shared secret) and RS256 (IAM
 * JWKS). RS256 keys are resolved by the token's {@code kid}; unknown key ids
 * trigger an on-demand, rate-limited JWKS refetch so IAM key rotation is picked
 * up without a gateway restart.</p>
 */
@Slf4j
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
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

    private static final List<String> API_KEY_PATHS = List.of(
            "/api/v1/merchant/",
            "/api/v1/merchant-order-ingest/"
    );

    private final SecretKey signingKey;
    private final boolean rs256;
    private final String issuer;
    private final String audience;
    private final JwksKeyStore jwksKeyStore;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayErrorWriter errorWriter;
    private final String blacklistPrefix;

    public JwtAuthenticationGlobalFilter(
            @Value("${swiftdrop.jwt.algorithm:HS256}") String algorithm,
            @Value("${swiftdrop.jwt.secret}") String jwtSecret,
            @Value("${swiftdrop.jwt.blacklist-prefix:jwt:blacklist:}") String blacklistPrefix,
            @Value("${swiftdrop.jwt.issuer:swiftdrop-iam}") String issuer,
            @Value("${swiftdrop.jwt.audience:swiftdrop}") String audience,
            ReactiveStringRedisTemplate redisTemplate,
            GatewayErrorWriter errorWriter,
            JwksKeyStore jwksKeyStore,
            ObjectMapper objectMapper) {
        if (!"HS256".equalsIgnoreCase(algorithm) && !"RS256".equalsIgnoreCase(algorithm)) {
            throw new IllegalArgumentException("JWT_ALGORITHM must be HS256 or RS256");
        }
        this.rs256 = "RS256".equalsIgnoreCase(algorithm);
        this.signingKey = this.rs256 ? null
                : Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.blacklistPrefix = blacklistPrefix;
        this.issuer = issuer;
        this.audience = audience;
        this.redisTemplate = redisTemplate;
        this.errorWriter = errorWriter;
        this.jwksKeyStore = jwksKeyStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isApiKeyPath(path)) {
            return chain.filter(exchange);
        }

        if (isPublicPath(path)) {
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

        return verify(token)
                .flatMap(claims -> forwardVerified(exchange, chain, path, token, claims))
                .onErrorResume(InvalidTokenException.class, exception -> {
                    log.warn("JWT validation failed for path {}: {}", path, exception.getMessage());
                    return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private Mono<Claims> verify(String token) {
        if (!rs256) {
            return Mono.fromCallable(() -> parse(token, signingKey))
                    .subscribeOn(Schedulers.boundedElastic());
        }
        return Mono.fromCallable(() -> JwsHeader.decode(token, objectMapper))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(exception -> new InvalidTokenException("Malformed token header"))
                .flatMap(header -> {
                    if (!"RS256".equals(header.algorithm())) {
                        return Mono.error(new InvalidTokenException("Unexpected signing algorithm"));
                    }
                    if (header.keyId() == null || header.keyId().isBlank()) {
                        return Mono.error(new InvalidTokenException("Token has no key id"));
                    }
                    return jwksKeyStore.resolve(header.keyId())
                            .switchIfEmpty(Mono.error(new InvalidTokenException("Unknown signing key")))
                            .flatMap(key -> Mono.fromCallable(() -> parse(token, key))
                                    .subscribeOn(Schedulers.boundedElastic()));
                });
    }

    private Claims parse(String token, SecretKey key) {
        try {
            return baseParser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException(exception.getMessage());
        }
    }

    private Claims parse(String token, PublicKey key) {
        try {
            return baseParser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException(exception.getMessage());
        }
    }

    private io.jsonwebtoken.JwtParserBuilder baseParser() {
        return Jwts.parser()
                .requireIssuer(issuer)
                .requireAudience(audience);
    }

    private Mono<Void> forwardVerified(ServerWebExchange exchange,
                                       GatewayFilterChain chain,
                                       String path,
                                       String token,
                                       Claims claims) {
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

    private static final class InvalidTokenException extends RuntimeException {
        private InvalidTokenException(String message) {
            super(message);
        }
    }
}
