package com.swiftdrop.webhook.resiliency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSignatureGeneratorTest {

    private final HmacSignatureGenerator generator = new HmacSignatureGenerator();

    @Test
    void producesTheKnownHmacSha256Value() {
        // Reference: printf 'payload' | openssl dgst -sha256 -hmac key
        assertThat(generator.calculateSignature("payload", "key"))
                .isEqualTo("sha256=5d98b45c90a207fa998ce639fea6f02ecc8cc3f36fef81d694fb856b4d0a28ca");
    }

    @Test
    void verifiesMatchingSignaturesOnly() {
        String signature = generator.calculateSignature("payload", "key");
        assertThat(generator.verifySignature("payload", "key", signature)).isTrue();
        assertThat(generator.verifySignature("payload!", "key", signature)).isFalse();
        assertThat(generator.verifySignature("payload", "other", signature)).isFalse();
        assertThat(generator.verifySignature("payload", "key", "md5=abc")).isFalse();
        assertThat(generator.verifySignature("payload", "key", null)).isFalse();
        assertThat(generator.verifySignature(null, "key", signature)).isFalse();
    }

    @Test
    void emptySecretIsRejected() {
        assertThatThrownBy(() -> generator.calculateSignature("payload", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(generator.verifySignature("payload", "", "sha256=00")).isFalse();
    }

    @Test
    void nullPayloadIsSignedAsEmpty() {
        assertThat(generator.calculateSignature(null, "key")).isEqualTo(generator.calculateSignature("", "key"));
    }
}
