package com.swiftdrop.gateway.filter;

import com.swiftdrop.contracts.dto.MerchantApiKeyValidationRequest;
import com.swiftdrop.contracts.dto.MerchantApiKeyValidationResult;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates merchant machine-to-machine requests using an API key.
 *
 * <p>The raw key is never forwarded downstream. It is validated by the merchant
 * service once, the resulting merchant ID is cached in Redis for a short TTL,
 * and the request is rewritten with trusted {@code X-Merchant-Id} and
 * {@code X-User-Role} headers.</p>
 */
@Slf4j
@Component
public class MerchantApiKeyGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> API_KEY_PATHS = List.of(
            "/api/v1/merchant/",
            "/api/v1/merchant-order-ingest/"
    );

    private final WebClient webClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayErrorWriter errorWriter;
    private final String validationUrl;
    private final long cacheTtlSeconds;

    public MerchantApiKeyGlobalFilter(
            WebClient.Builder webClientBuilder,
            ReactiveStringRedisTemplate redisTemplate,
            GatewayErrorWriter errorWriter,
            @Value("${swiftdrop.merchant.api-key-validation-url}") String validationUrl,
            @Value("${swiftdrop.merchant.api-key-cache-ttl-seconds:60}") long cacheTtlSeconds) {
        this.webClient = webClientBuilder.build();
        this.redisTemplate = redisTemplate;
        this.errorWriter = errorWriter;
        this.validationUrl = validationUrl;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!isProtectedPath(path)) {
            return chain.filter(exchange);
        }

        String apiKey = firstNonBlank(
                exchange.getRequest().getHeaders().getFirst(JwtHeaderConstants.X_API_KEY),
                exchange.getRequest().getHeaders().getFirst(JwtHeaderConstants.X_MERCHANT_API_KEY)
        );

        if (apiKey == null) {
            log.warn("Missing merchant API key for path: {}", path);
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing merchant API key");
        }

        String cacheKey = "merchant:api-key:" + sha256(apiKey);

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .onErrorResume(exception -> {
                    log.warn("Redis API-key cache unavailable: {}", exception.getMessage());
                    return Mono.empty();
                })
                .filter(cachedMerchantId -> !cachedMerchantId.isBlank())
                .flatMap(cachedMerchantId -> continueWithMerchant(exchange, chain, cachedMerchantId))
                .switchIfEmpty(Mono.defer(() -> validateWithMerchantService(exchange, chain, apiKey, cacheKey)));
    }

    private Mono<Void> validateWithMerchantService(ServerWebExchange exchange,
                                                   GatewayFilterChain chain,
                                                   String apiKey,
                                                   String cacheKey) {
        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(JwtHeaderConstants.X_CORRELATION_ID);

        return webClient.post()
                .uri(validationUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (correlationId != null) {
                        headers.set(JwtHeaderConstants.X_CORRELATION_ID, correlationId);
                    }
                })
                .bodyValue(new MerchantApiKeyValidationRequest(apiKey))
                .retrieve()
                .bodyToMono(MerchantApiKeyValidationResult.class)
                .timeout(Duration.ofSeconds(2))
                .flatMap(result -> {
                    if (!result.valid() || result.merchantId() == null) {
                        log.warn("Merchant API key rejected: {}", result.reason());
                        return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                                "Invalid merchant API key");
                    }
                    return cacheMerchant(cacheKey, result.merchantId())
                            .then(continueWithMerchant(exchange, chain, result.merchantId().toString()));
                })
                .onErrorResume(exception -> {
                    log.error("Merchant API key validation failed: {}", exception.getMessage());
                    return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            "Merchant authentication is temporarily unavailable");
                });
    }

    private Mono<Void> cacheMerchant(String cacheKey, UUID merchantId) {
        return redisTemplate.opsForValue()
                .set(cacheKey, merchantId.toString(), Duration.ofSeconds(cacheTtlSeconds))
                .onErrorResume(exception -> {
                    log.warn("Unable to cache merchant API key result: {}", exception.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> continueWithMerchant(ServerWebExchange exchange,
                                            GatewayFilterChain chain,
                                            String merchantId) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(JwtHeaderConstants.X_API_KEY);
                    headers.remove(JwtHeaderConstants.X_MERCHANT_API_KEY);
                    headers.remove(JwtHeaderConstants.X_USER_ID);
                    headers.remove(JwtHeaderConstants.X_USER_ROLE);
                    headers.remove(JwtHeaderConstants.X_MERCHANT_ID);
                    headers.remove(JwtHeaderConstants.X_DISTRICT_ID);
                    headers.remove("Authorization");
                })
                .header(JwtHeaderConstants.X_MERCHANT_ID, merchantId)
                .header(JwtHeaderConstants.X_USER_ID, merchantId)
                .header(JwtHeaderConstants.X_USER_ROLE, "MERCHANT")
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -150;
    }

    private boolean isProtectedPath(String path) {
        return API_KEY_PATHS.stream().anyMatch(path::startsWith);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
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
