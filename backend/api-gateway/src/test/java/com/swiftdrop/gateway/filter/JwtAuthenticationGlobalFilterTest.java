package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.gateway.security.JwksKeyStore;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationGlobalFilterTest {

    private static final String SECRET = "gateway-test-secret-key-must-be-at-least-256-bits-long!!";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String ISSUER = "swiftdrop-iam";
    private static final String AUDIENCE = "swiftdrop";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtAuthenticationGlobalFilter filter;

    @BeforeEach
    void setUp() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        filter = new JwtAuthenticationGlobalFilter(
                "HS256", SECRET, BLACKLIST_PREFIX, ISSUER, AUDIENCE,
                redisTemplate, new GatewayErrorWriter(objectMapper),
                mock(JwksKeyStore.class), objectMapper);
    }

    @Test
    void missingTokenOnProtectedPathReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123").build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    @Test
    void validTokenReplacesForgedIdentityHeaders() {
        UUID customerId = UUID.randomUUID();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(customerId, "CUSTOMER", Duration.ofHours(1)))
                        .header(JwtHeaderConstants.X_USER_ID, UUID.randomUUID().toString())
                        .header(JwtHeaderConstants.X_USER_ROLE, "SYSTEM_ADMIN")
                        .build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwarded = chain.exchange.get();
        assertNotNull(forwarded);
        assertEquals(customerId.toString(), forwarded.getRequest().getHeaders()
                .getFirst(JwtHeaderConstants.X_USER_ID));
        assertEquals("CUSTOMER", forwarded.getRequest().getHeaders()
                .getFirst(JwtHeaderConstants.X_USER_ROLE));
    }

    @Test
    void forgedIdentityHeaderIsRemovedOnPublicPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/public/track/SD-1234-5678")
                        .header(JwtHeaderConstants.X_USER_ROLE, "SYSTEM_ADMIN")
                        .build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        ServerWebExchange forwarded = chain.exchange.get();
        assertNotNull(forwarded);
        assertNull(forwarded.getRequest().getHeaders().getFirst(JwtHeaderConstants.X_USER_ROLE));
    }

    @Test
    void merchantApiKeyPathSkipsJwtValidation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/merchant-order-ingest/orders").build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        assertNotNull(chain.exchange.get());
    }

    @Test
    void expiredTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(UUID.randomUUID(), "CUSTOMER", Duration.ofMinutes(-1)))
                        .build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    @Test
    void wrongIssuerReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(UUID.randomUUID(), "CUSTOMER", Duration.ofHours(1),
                                        "evil-issuer", AUDIENCE))
                        .build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    @Test
    void wrongAudienceReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(UUID.randomUUID(), "CUSTOMER", Duration.ofHours(1),
                                        ISSUER, "other-audience"))
                        .build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    private String token(UUID userId, String role, Duration lifetime) {
        return token(userId, role, lifetime, ISSUER, AUDIENCE);
    }

    private String token(UUID userId, String role, Duration lifetime, String issuer, String audience) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(SIGNING_KEY)
                .compact();
    }

    private static final class RecordingChain implements GatewayFilterChain {

        private final AtomicReference<ServerWebExchange> exchange = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.exchange.set(exchange);
            return Mono.empty();
        }
    }
}
