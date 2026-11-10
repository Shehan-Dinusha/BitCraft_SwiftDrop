package com.swiftdrop.gateway.trackinghub.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.gateway.security.JwksKeyStore;
import io.jsonwebtoken.Jwts;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

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

import static org.assertj.core.api.Assertions.assertThat;

class TrackingTokenVerifierRs256Test {

    private static final String ISSUER = "swiftdrop-iam";
    private static final String AUDIENCE = "swiftdrop";
    private static final String KID = "rsa-test-kid";

    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;
    private KeyPair keyPair;
    private JwksKeyStore jwksKeyStore;
    private TrackingTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        objectMapper = new ObjectMapper();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        jwksKeyStore = new JwksKeyStore(WebClient.builder().build(), objectMapper,
                mockWebServer.url("/jwks.json").toString(), 300, 5);
        verifier = new TrackingTokenVerifier(
                "RS256", null, ISSUER, AUDIENCE, jwksKeyStore, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void resolvesPrincipalWhenKeyIsCached() {
        enqueueJwks(KID);
        jwksKeyStore.resolve(KID).block();

        TrackingTokenVerifier.StompPrincipal principal =
                verifier.verify("Bearer " + token("customer-123", KID, ISSUER, AUDIENCE));

        assertThat(principal).isNotNull();
        assertThat(principal.subject()).isEqualTo("customer-123");
        assertThat(principal.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void rejectsTokenWhenKeyIsNotCached() {
        enqueueJwks("another-kid");

        TrackingTokenVerifier.StompPrincipal principal =
                verifier.verify("Bearer " + token("customer-123", KID, ISSUER, AUDIENCE));

        assertThat(principal).isNull();
    }

    @Test
    void rejectsTokenWithWrongIssuerOrAudience() {
        enqueueJwks(KID);
        jwksKeyStore.resolve(KID).block();

        assertThat(verifier.verify("Bearer "
                + token("customer-123", KID, "evil-issuer", AUDIENCE))).isNull();
        assertThat(verifier.verify("Bearer "
                + token("customer-123", KID, ISSUER, "other-audience"))).isNull();
    }

    @Test
    void rejectsHs256TokenWhenRs256IsConfigured() {
        String hs256 = Jwts.builder()
                .header().keyId(KID).and()
                .subject("customer-123")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThat(verifier.verify("Bearer " + hs256)).isNull();
    }

    private String token(String subject, String kid, String issuer, String audience) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(kid).and()
                .subject(subject)
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("role", "CUSTOMER")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
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
}
