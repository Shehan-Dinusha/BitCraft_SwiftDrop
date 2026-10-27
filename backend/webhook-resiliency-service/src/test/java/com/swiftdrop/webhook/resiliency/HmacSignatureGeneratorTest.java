package com.swiftdrop.webhook.resiliency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureGeneratorTest {

    private HmacSignatureGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new HmacSignatureGenerator();
    }

    @Test
    @DisplayName("calculateSignature should return sha256 hex digest prefixed with sha256=")
    void testCalculateSignature() {
        String payload = "{\"event\":\"parcel.status.changed\",\"status\":\"DELIVERED\"}";
        String secret = "whsec_test_secret_key_123";

        String signature = generator.calculateSignature(payload, secret);

        assertThat(signature).isNotNull();
        assertThat(signature).startsWith("sha256=");
        assertThat(signature.length()).isEqualTo(7 + 64); // "sha256=" + 64 hex characters
    }

    @Test
    @DisplayName("verifySignature should return true for identical payload and secret")
    void testVerifySignatureSuccess() {
        String payload = "{\"trackingId\":\"SD-2026-COL-001\",\"status\":\"IN_TRANSIT\"}";
        String secret = "whsec_secret_abc_456";

        String signature = generator.calculateSignature(payload, secret);

        boolean isValid = generator.verifySignature(payload, secret, signature);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("verifySignature should return false for tampered payload")
    void testVerifySignatureTamperedPayload() {
        String originalPayload = "{\"amount\":1000}";
        String tamperedPayload = "{\"amount\":9999}";
        String secret = "whsec_secret_xyz";

        String signature = generator.calculateSignature(originalPayload, secret);

        boolean isValid = generator.verifySignature(tamperedPayload, secret, signature);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("verifySignature should return false for wrong secret")
    void testVerifySignatureWrongSecret() {
        String payload = "{\"test\":true}";
        String secret1 = "whsec_secret_one";
        String secret2 = "whsec_secret_two";

        String signature = generator.calculateSignature(payload, secret1);

        boolean isValid = generator.verifySignature(payload, secret2, signature);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("verifySignature should return false for null or empty values")
    void testVerifySignatureNullOrEmpty() {
        assertThat(generator.verifySignature(null, "secret", "sha256=123")).isFalse();
        assertThat(generator.verifySignature("payload", null, "sha256=123")).isFalse();
        assertThat(generator.verifySignature("payload", "secret", null)).isFalse();
        assertThat(generator.verifySignature("payload", "secret", "invalid_sig")).isFalse();
    }
}
