package com.swiftdrop.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwksKeyStoreTest {

    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        objectMapper = new ObjectMapper();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void resolveFetchesAndCachesKey() {
        enqueueJwks("kid-1", keyPair);
        JwksKeyStore store = newStore(300, 5);

        RSAPublicKey first = store.resolve("kid-1").block();
        RSAPublicKey second = store.resolve("kid-1").block();

        assertThat(first).isEqualTo(keyPair.getPublic());
        assertThat(second).isEqualTo(keyPair.getPublic());
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void unknownKidIsRateLimitedByRefetchCooldown() {
        enqueueJwks("kid-1", keyPair);
        JwksKeyStore store = newStore(300, 5);

        assertThat(store.resolve("missing").block()).isNull();
        assertThat(store.resolve("missing").block()).isNull();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void refetchesUnknownKidAfterCooldown() {
        enqueueJwks("kid-1", keyPair);
        enqueueJwks("kid-1", keyPair);
        JwksKeyStore store = newStore(300, 0);

        assertThat(store.resolve("missing").block()).isNull();
        assertThat(store.resolve("missing").block()).isNull();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    void cachedKeyReturnsNullBeforeFirstFetch() throws Exception {
        JwksKeyStore store = newStore(300, 5);

        assertThat(store.cached("kid-1")).isNull();

        enqueueJwks("kid-1", keyPair);
        store.resolve("kid-1").block();

        assertThat(store.cached("kid-1")).isEqualTo(keyPair.getPublic());
        assertThat(store.cached("missing")).isNull();
    }

    @Test
    void parseKeysSkipsUnsupportedJwksEntries() throws Exception {
        JwksKeyStore store = newStore(300, 5);
        String body = objectMapper.writeValueAsString(Map.of("keys", List.of(
                Map.of("kty", "EC", "kid", "ec-key"),
                Map.of("kty", "RSA", "kid", "hs-key", "alg", "HS256",
                        "n", encodedModulus(), "e", encodedExponent()),
                Map.of("kty", "RSA", "kid", "enc-key", "use", "enc",
                        "n", encodedModulus(), "e", encodedExponent())
        )));

        Map<String, RSAPublicKey> parsed = store.parseKeys(body);

        assertThat(parsed).isEmpty();
    }

    @Test
    void parseKeysRejectsMalformedDocument() {
        JwksKeyStore store = newStore(300, 5);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.parseKeys("not-json"))
                .isInstanceOf(IllegalStateException.class);
    }

    private JwksKeyStore newStore(long ttlSeconds, long cooldownSeconds) {
        WebClient webClient = WebClient.builder().build();
        return new JwksKeyStore(webClient, objectMapper,
                mockWebServer.url("/jwks.json").toString(), ttlSeconds, cooldownSeconds);
    }

    private void enqueueJwks(String kid, KeyPair pair) {
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
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

    private String encodedModulus() {
        return base64Url(((RSAPublicKey) keyPair.getPublic()).getModulus());
    }

    private String encodedExponent() {
        return base64Url(((RSAPublicKey) keyPair.getPublic()).getPublicExponent());
    }

    private String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
