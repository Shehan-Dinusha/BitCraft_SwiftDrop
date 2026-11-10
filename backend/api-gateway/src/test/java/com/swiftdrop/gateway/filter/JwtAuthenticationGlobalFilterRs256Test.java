package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.gateway.security.JwksKeyStore;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationGlobalFilterRs256Test {

    private static final String SECRET = "gateway-test-secret-key-must-be-at-least-256-bits-long!!";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String ISSUER = "swiftdrop-iam";
    private static final String AUDIENCE = "swiftdrop";
    private static final String KID = "rsa-test-kid";

    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;
    private KeyPair keyPair;
    private JwtAuthenticationGlobalFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        objectMapper = new ObjectMapper().findAndRegisterModules();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        WebClient webClient = WebClient.builder().build();
        JwksKeyStore jwksKeyStore = new JwksKeyStore(webClient, objectMapper,
                mockWebServer.url("/jwks.json").toString(), 300, 5);

        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        filter = new JwtAuthenticationGlobalFilter(
                "RS256", SECRET, BLACKLIST_PREFIX, ISSUER, AUDIENCE,
                redisTemplate, new GatewayErrorWriter(objectMapper),
                jwksKeyStore, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void validRs256TokenForwardsIdentityHeaders() {
        enqueueJwks(KID);
        UUID customerId = UUID.randomUUID();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rs256Token(customerId, KID))
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
    void unknownKidReturnsUnauthorized() {
        enqueueJwks("another-kid");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rs256Token(UUID.randomUUID(), KID))
                        .build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    @Test
    void hs256TokenIsRejectedWhenRs256IsConfigured() {
        UUID customerId = UUID.randomUUID();
        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject(customerId.toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    @Test
    void tokenSignedByUntrustedKeyReturnsUnauthorized() throws Exception {
        enqueueJwks(KID);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair attacker = generator.generateKeyPair();

        String token = Jwts.builder()
                .header().keyId(KID).and()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(attacker.getPrivate(), Jwts.SIG.RS256)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
        RecordingChain chain = new RecordingChain();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    private String rs256Token(UUID userId, String kid) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(kid).and()
                .subject(userId.toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("role", "CUSTOMER")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private void enqueueJwks(String kid) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        try {
            String body = objectMapper.writeValueAsString(Map.of("keys", List.of(Map.of(
                    "kty", "RSA",
                    "kid", kid,
                    "use", "sig",
                    "alg", "RS256",
                    "n", base64Url(publicKey.getModulus()),
                    "e", base64Url(publicKey.getPublicExponent())
            ))));
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .setBody(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
