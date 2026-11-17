package com.swiftdrop.webhook.delivery;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The webhook signature contract merchants verify against. */
class WebhookSignerTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final byte[] BODY = "{\"event\":\"parcel.delivered\"}".getBytes(StandardCharsets.UTF_8);
    private final WebhookSigner signer = new WebhookSigner(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void signatureIsDeterministicAndWellFormed() {
        String first = signer.sign("whsec_test", NOW.getEpochSecond(), BODY);
        assertThat(first).matches("^sha256=[0-9a-f]{64}$");
        assertThat(signer.sign("whsec_test", NOW.getEpochSecond(), BODY)).isEqualTo(first);
        assertThat(signer.sign("whsec_other", NOW.getEpochSecond(), BODY)).isNotEqualTo(first);
    }

    @Test
    void verifyAcceptsAFreshCorrectSignature() {
        long ts = NOW.getEpochSecond() - 60;
        assertThat(signer.verify("whsec_test", String.valueOf(ts), signer.sign("whsec_test", ts, BODY), BODY)).isTrue();
    }

    @Test
    void verifyRejectsTamperingReplayAndMalformedHeaders() {
        long ts = NOW.getEpochSecond();
        String signature = signer.sign("whsec_test", ts, BODY);
        byte[] tampered = "{\"event\":\"parcel.cancelled\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(signer.verify("whsec_test", String.valueOf(ts), signature, tampered)).isFalse();
        assertThat(signer.verify("whsec_wrong", String.valueOf(ts), signature, BODY)).isFalse();
        long old = ts - WebhookSigner.TOLERANCE.toSeconds() - 1;
        assertThat(signer.verify("whsec_test", String.valueOf(old), signer.sign("whsec_test", old, BODY), BODY)).isFalse();
        assertThat(signer.verify("whsec_test", "-1", signature, BODY)).isFalse();
        assertThat(signer.verify("whsec_test", String.valueOf(ts), "sha256=XYZ", BODY)).isFalse();
        assertThat(signer.verify("whsec_test", null, signature, BODY)).isFalse();
    }

    @Test
    void signRejectsInvalidInput() {
        assertThatThrownBy(() -> signer.sign("", 1, BODY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.sign("k", -1, BODY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.sign("k", 1, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
