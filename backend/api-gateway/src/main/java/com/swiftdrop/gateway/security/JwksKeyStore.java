package com.swiftdrop.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resolves RSA public keys from the IAM JWKS endpoint and caches them by
 * {@code kid}. Unknown key ids trigger an on-demand refetch, throttled by a
 * cooldown so forged tokens cannot stampede the IAM service.
 *
 * <p>The store is intentionally shared by the HTTP JWT filter and the STOMP
 * handshake so both verify the same rotation of keys.</p>
 */
@Slf4j
@Component
public class JwksKeyStore {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String jwksUrl;
    private final long cacheTtlMillis;
    private final long refetchCooldownMillis;

    private final AtomicLong lastFetchMillis = new AtomicLong(0);

    private volatile Map<String, RSAPublicKey> keys = Map.of();
    private volatile long keysLoadedAtMillis = 0;

    public JwksKeyStore(WebClient webClient,
                        ObjectMapper objectMapper,
                        @Value("${swiftdrop.jwt.jwks-url:}") String jwksUrl,
                        @Value("${swiftdrop.jwt.jwks-cache-ttl-seconds:300}") long cacheTtlSeconds,
                        @Value("${swiftdrop.jwt.jwks-refetch-cooldown-seconds:5}") long refetchCooldownSeconds) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.jwksUrl = jwksUrl;
        this.cacheTtlMillis = Duration.ofSeconds(cacheTtlSeconds).toMillis();
        this.refetchCooldownMillis = Duration.ofSeconds(refetchCooldownSeconds).toMillis();
    }

    public Mono<RSAPublicKey> resolve(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return Mono.empty();
        }
        RSAPublicKey cached = keys.get(keyId);
        if (cached != null && !isStale()) {
            return Mono.just(cached);
        }
        return refreshAndResolve(keyId);
    }

    /** Returns a cached key without fetching; used by the synchronous STOMP handshake. */
    public RSAPublicKey cached(String keyId) {
        return keyId == null || keyId.isBlank() ? null : keys.get(keyId);
    }

    /** Best-effort background refresh, throttled by the refetch cooldown. */
    public void refreshAsync() {
        if (jwksUrl == null || jwksUrl.isBlank() || !acquireFetchSlot()) {
            return;
        }
        fetchKeys().subscribe(
                ignored -> { },
                error -> log.warn("Background JWKS refresh failed: {}", error.getMessage()));
    }

    private Mono<RSAPublicKey> refreshAndResolve(String keyId) {
        RSAPublicKey cached = keys.get(keyId);
        if (!acquireFetchSlot()) {
            return Mono.justOrEmpty(cached);
        }
        return fetchKeys()
                .flatMap(loaded -> Mono.justOrEmpty(loaded.get(keyId)))
                .switchIfEmpty(Mono.justOrEmpty(cached))
                .onErrorResume(error -> {
                    log.warn("JWKS lookup failed for kid {}: {}", keyId, error.getMessage());
                    return Mono.justOrEmpty(cached);
                });
    }

    private boolean acquireFetchSlot() {
        long now = System.currentTimeMillis();
        long last = lastFetchMillis.get();
        if (now - last < refetchCooldownMillis) {
            return false;
        }
        return lastFetchMillis.compareAndSet(last, now);
    }

    private Mono<Map<String, RSAPublicKey>> fetchKeys() {
        if (jwksUrl == null || jwksUrl.isBlank()) {
            return Mono.error(new IllegalStateException("JWKS URL is not configured"));
        }
        return webClient.get()
                .uri(jwksUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseKeys)
                .doOnNext(loaded -> {
                    keys = loaded;
                    keysLoadedAtMillis = System.currentTimeMillis();
                });
    }

    Map<String, RSAPublicKey> parseKeys(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode keyNodes = root.get("keys");
            Map<String, RSAPublicKey> parsed = new LinkedHashMap<>();
            if (keyNodes != null && keyNodes.isArray()) {
                for (JsonNode keyNode : keyNodes) {
                    String keyId = text(keyNode, "kid");
                    RSAPublicKey key = parseKey(keyNode);
                    if (key != null && keyId != null && !keyId.isBlank()) {
                        parsed.put(keyId, key);
                    }
                }
            }
            return Map.copyOf(parsed);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse JWKS document", exception);
        }
    }

    private RSAPublicKey parseKey(JsonNode keyNode) {
        String keyType = text(keyNode, "kty");
        String algorithm = text(keyNode, "alg");
        String use = text(keyNode, "use");
        String modulus = text(keyNode, "n");
        String exponent = text(keyNode, "e");
        if (!"RSA".equals(keyType) || modulus == null || exponent == null) {
            return null;
        }
        if (algorithm != null && !"RS256".equals(algorithm)) {
            return null;
        }
        if (use != null && !"sig".equals(use)) {
            return null;
        }
        try {
            BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
            BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(n, e));
        } catch (Exception exception) {
            log.warn("Skipping malformed JWK: {}", exception.getMessage());
            return null;
        }
    }

    private boolean isStale() {
        return System.currentTimeMillis() - keysLoadedAtMillis >= cacheTtlMillis;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
