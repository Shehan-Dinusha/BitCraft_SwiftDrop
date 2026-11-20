package com.swiftdrop.notification.config;

import com.swiftdrop.notification.service.push.EcKeys;
import com.swiftdrop.notification.service.push.WebPushClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushConfigTest {

    @Test
    @DisplayName("No keys: the service starts with device push off (local development stays zero-config)")
    void missingKeysDisablePush() {
        WebPushClient client = WebPushConfig.create("", "", "");
        assertThat(client.enabled()).isFalse();
        assertThat(client.publicKey()).isNull();
        assertThat(WebPushConfig.create(null, null, null).enabled()).isFalse();
        assertThat(WebPushConfig.create("x", "y", "").enabled()).isFalse();
    }

    @Test
    void validKeysEnablePush() {
        KeyPair pair = EcKeys.generate();
        String pub = EcKeys.base64Url(EcKeys.encode((ECPublicKey) pair.getPublic()));
        WebPushClient client = WebPushConfig.create(pub, EcKeys.base64Url(EcKeys.encode((ECPrivateKey) pair.getPrivate())),
                "mailto:ops@swiftdrop.example");
        assertThat(client.enabled()).isTrue();
        assertThat(client.publicKey()).isEqualTo(pub);
    }

    @Test
    @DisplayName("Keys that are set but broken stop startup with a clear message")
    void brokenKeysFailFast() {
        assertThatThrownBy(() -> WebPushConfig.create("not-a-key", "also-not", "mailto:a@b.c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generate-helm-secrets.sh");
    }
}
