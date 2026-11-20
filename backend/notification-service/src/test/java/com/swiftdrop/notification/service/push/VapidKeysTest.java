package com.swiftdrop.notification.service.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VapidKeysTest {

    private static final KeyPair PAIR = EcKeys.generate();
    private static final String PUBLIC = EcKeys.base64Url(EcKeys.encode((ECPublicKey) PAIR.getPublic()));
    private static final String PRIVATE = EcKeys.base64Url(EcKeys.encode((ECPrivateKey) PAIR.getPrivate()));

    @Test
    @DisplayName("Builds 'vapid t=<ES256 JWT>, k=<key>' with the push service origin as audience")
    void authorizationHeader() throws Exception {
        VapidKeys keys = VapidKeys.of(PUBLIC, PRIVATE, "mailto:ops@swiftdrop.example");
        Instant now = Instant.parse("2026-11-20T10:00:00Z");

        String header = keys.authorization("https://fcm.googleapis.com/fcm/send/abc", now);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + PUBLIC);
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        ObjectMapper json = new ObjectMapper();
        JsonNode head = json.readTree(EcKeys.decodeBase64Url(parts[0]));
        JsonNode claims = json.readTree(EcKeys.decodeBase64Url(parts[1]));
        assertThat(head.get("alg").asText()).isEqualTo("ES256");
        assertThat(claims.get("aud").asText()).isEqualTo("https://fcm.googleapis.com");
        assertThat(claims.get("sub").asText()).isEqualTo("mailto:ops@swiftdrop.example");
        assertThat(claims.get("exp").asLong()).isEqualTo(now.plus(VapidKeys.TOKEN_LIFETIME).getEpochSecond());
        byte[] signature = EcKeys.decodeBase64Url(parts[2]);
        assertThat(signature).hasSize(64); // raw R || S, as JWS requires, not DER
        assertThat(keys.verify((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII), signature)).isTrue();
    }

    @Test
    void keepsAnExplicitPortInTheAudience() throws Exception {
        String header = VapidKeys.of(PUBLIC, PRIVATE, "https://swiftdrop.example").authorization("https://push.example:8443/x", Instant.now());
        String claims = new String(EcKeys.decodeBase64Url(header.substring(8).split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://push.example:8443\"");
    }

    @Test
    @DisplayName("Refuses keys that are not a pair, malformed keys and a missing contact")
    void validation() {
        String otherPublic = EcKeys.base64Url(EcKeys.encode((ECPublicKey) EcKeys.generate().getPublic()));
        assertThatThrownBy(() -> VapidKeys.of(otherPublic, PRIVATE, "mailto:a@b.c"))
                .hasMessageContaining("does not belong");
        assertThatThrownBy(() -> VapidKeys.of("abc", PRIVATE, "mailto:a@b.c"))
                .hasMessageContaining("not base64url P-256");
        assertThatThrownBy(() -> VapidKeys.of(PUBLIC, PRIVATE, "ops@swiftdrop.example"))
                .hasMessageContaining("mailto: or https:");
    }

    @Test
    void neverPrintsThePrivateKey() {
        assertThat(VapidKeys.of(PUBLIC, PRIVATE, "mailto:a@b.c").toString()).doesNotContain(PRIVATE).contains(PUBLIC);
    }
}
