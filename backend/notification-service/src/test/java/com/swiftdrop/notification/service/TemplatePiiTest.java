package com.swiftdrop.notification.service;

import com.swiftdrop.notification.domain.entity.NotificationTemplate;
import com.swiftdrop.notification.domain.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplatePiiTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @InjectMocks
    private TemplateRendererService templateRenderer;

    @Test
    @DisplayName("Rendered template must never contain plaintext passwords or secrets")
    void testRenderedOutputNeverContainsPasswords() {
        String template = "Security alert: Your temporary password is {{password}} and pin is {{pin}}.";
        Map<String, String> params = Map.of(
                "password", "TopSecretPass999!",
                "pin", "4321"
        );

        String rendered = templateRenderer.interpolate(template, params);

        assertThat(rendered)
                .doesNotContain("TopSecretPass999!")
                .doesNotContain("4321");
    }

    @Test
    @DisplayName("Rendered template must never contain payment card numbers")
    void testRenderedOutputNeverContainsPaymentCardNumbers() {
        String template = "Receipt: Paid with card {{cardNumber}}. Transaction code {{txCode}}.";
        Map<String, String> params = Map.of(
                "cardNumber", "4111222233334444",
                "txCode", "TX-998811"
        );

        String rendered = templateRenderer.interpolate(template, params);

        assertThat(rendered)
                .doesNotContain("4111222233334444")
                .contains("TX-998811");
    }

    @Test
    @DisplayName("Rendered template must never contain full street address")
    void testRenderedOutputNeverContainsFullStreetAddress() {
        String template = "Delivery address: {{fullAddress}} for parcel {{trackingNumber}}.";
        Map<String, String> params = Map.of(
                "fullAddress", "No. 45/A, Temple Road, Colombo 03, Sri Lanka",
                "trackingNumber", "SD-12345"
        );

        String rendered = templateRenderer.interpolate(template, params);

        assertThat(rendered)
                .doesNotContain("No. 45/A, Temple Road, Colombo 03, Sri Lanka")
                .contains("SD-12345");
    }

    @Test
    @DisplayName("Rendered template must never contain full phone numbers, only masked phones")
    void testRenderedOutputNeverContainsFullPhoneNumber() {
        String template = "Courier contact: {{phone}}. For inquiries contact {{contactNumber}}.";
        Map<String, String> params = Map.of(
                "phone", "0771235678",
                "contactNumber", "+94719991234"
        );

        String rendered = templateRenderer.interpolate(template, params);

        // Raw phone numbers must NOT be exposed
        assertThat(rendered)
                .doesNotContain("0771235678")
                .doesNotContain("+94719991234");

        // Masked phone representations must be used
        assertThat(rendered)
                .contains("07X***5678")
                .contains("07X***1234");
    }

    @Test
    @DisplayName("Unknown or unmapped placeholders must render empty rather than leaking raw tags")
    void testUnknownPlaceholdersRenderEmpty() {
        String template = "Parcel {{trackingNumber}} status {{status}}. Notes: {{internalNotes}} and {{unknownField}}.";
        Map<String, String> params = Map.of(
                "trackingNumber", "SD-556677",
                "status", "DELIVERED"
        );

        String rendered = templateRenderer.interpolate(template, params);

        assertThat(rendered)
                .isEqualTo("Parcel SD-556677 status DELIVERED. Notes:  and .")
                .doesNotContain("{{internalNotes}}")
                .doesNotContain("{{unknownField}}");
    }

    @Test
    @DisplayName("Full renderTemplate flow enforces PII sanitization and empty unmapped placeholders")
    void testFullRenderTemplatePiiSafety() {
        NotificationTemplate template = NotificationTemplate.builder()
                .templateCode("DELIVERY_CONFIRMATION")
                .channel("EMAIL")
                .subjectTemplate("Delivery {{trackingNumber}} - {{missingSubjectTag}}")
                .bodyTemplate("Hello, your order {{trackingNumber}} was delivered to {{fullAddress}}. Card {{creditCard}}. Pin: {{pin}}. Contact: {{phone}}.")
                .active(true)
                .build();

        when(templateRepository.findByTemplateCodeAndActiveTrue("DELIVERY_CONFIRMATION"))
                .thenReturn(Optional.of(template));

        var rendered = templateRenderer.renderTemplate("DELIVERY_CONFIRMATION", Map.of(
                "trackingNumber", "SD-0099",
                "fullAddress", "123 Main Street, Suite 4B, Kandy",
                "creditCard", "5500000011112222",
                "pin", "9876",
                "phone", "0761112222"
        ));

        // Subject check
        assertThat(rendered.subject())
                .isEqualTo("Delivery SD-0099 - ")
                .doesNotContain("{{missingSubjectTag}}");

        // Body check
        assertThat(rendered.body())
                .contains("SD-0099")
                .contains("07X***2222")
                .doesNotContain("123 Main Street")
                .doesNotContain("5500000011112222")
                .doesNotContain("9876")
                .doesNotContain("0761112222");
    }
}
