package com.swiftdrop.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.service.email.AzureCommunicationEmailSender;
import com.swiftdrop.notification.service.email.MockEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailSenderConfigTest {

    private static final String ENDPOINT = "https://swiftdrop-test.asiapacific.communication.azure.com";
    private static final String KEY = Base64.getEncoder().encodeToString("test-access-key-0123456789".getBytes(StandardCharsets.UTF_8));
    private static final String SENDER = "DoNotReply@test.azurecomm.net";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Defaults to the mock provider so nothing is sent by accident")
    void defaultsToMock() {
        assertThat(EmailSenderConfig.create(null, "", "", "", objectMapper)).isInstanceOf(MockEmailSender.class);
        assertThat(EmailSenderConfig.create("", "", "", "", objectMapper)).isInstanceOf(MockEmailSender.class);
        assertThat(EmailSenderConfig.create("MOCK", "", "", "", objectMapper)).isInstanceOf(MockEmailSender.class);
    }

    @Test
    @DisplayName("Selects Azure when all three settings are valid")
    void selectsAzure() {
        assertThat(EmailSenderConfig.create("azure", ENDPOINT, KEY, SENDER, objectMapper))
                .isInstanceOf(AzureCommunicationEmailSender.class)
                .extracting(sender -> sender.providerName()).isEqualTo("azure");
    }

    @Test
    @DisplayName("Azure fails fast when a setting is missing or malformed")
    void azureFailsFast() {
        assertThatThrownBy(() -> EmailSenderConfig.create("azure", "", KEY, SENDER, objectMapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ACS_EMAIL_ENDPOINT");
        assertThatThrownBy(() -> EmailSenderConfig.create("azure", "http://insecure.example", KEY, SENDER, objectMapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("https://");
        assertThatThrownBy(() -> EmailSenderConfig.create("azure", ENDPOINT, "", SENDER, objectMapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ACS_EMAIL_ACCESS_KEY");
        assertThatThrownBy(() -> EmailSenderConfig.create("azure", ENDPOINT, KEY, "not-an-address", objectMapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ACS_EMAIL_SENDER_ADDRESS");
    }

    @Test
    @DisplayName("A malformed key is rejected without echoing it")
    void malformedKeyNotEchoed() {
        String badKey = "not*base64*secret";
        assertThatThrownBy(() -> EmailSenderConfig.create("azure", ENDPOINT, badKey, SENDER, objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .message().doesNotContain(badKey);
    }

    @Test
    @DisplayName("An unknown provider is rejected")
    void unknownProviderRejected() {
        assertThatThrownBy(() -> EmailSenderConfig.create("smtp", ENDPOINT, KEY, SENDER, objectMapper))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'mock' or 'azure'");
    }
}
