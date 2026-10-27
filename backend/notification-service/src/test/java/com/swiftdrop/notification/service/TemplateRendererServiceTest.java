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
class TemplateRendererServiceTest {

    @Mock
    private NotificationTemplateRepository templateRepository;

    @InjectMocks
    private TemplateRendererService templateRenderer;

    @Test
    @DisplayName("Interpolate should replace placeholders with map values")
    void testInterpolate() {
        String tmpl = "Hello {{name}}, your tracking code is {{code}}.";
        Map<String, String> params = Map.of("name", "Nimal", "code", "SD-12345");

        String result = templateRenderer.interpolate(tmpl, params);
        assertThat(result).isEqualTo("Hello Nimal, your tracking code is SD-12345.");
    }

    @Test
    @DisplayName("Interpolate should handle null or empty inputs gracefully")
    void testInterpolateEdgeCases() {
        assertThat(templateRenderer.interpolate(null, Map.of())).isEmpty();
        assertThat(templateRenderer.interpolate("Constant text", null)).isEqualTo("Constant text");
        assertThat(templateRenderer.interpolate("Constant text", Map.of())).isEqualTo("Constant text");
    }

    @Test
    @DisplayName("Render template from database when present")
    void testRenderFromDatabase() {
        NotificationTemplate template = NotificationTemplate.builder()
                .templateCode("CUSTOM_ALERT")
                .channel("SMS")
                .subjectTemplate("Alert: {{topic}}")
                .bodyTemplate("Notice: {{message}} for customer {{customerName}}.")
                .active(true)
                .build();

        when(templateRepository.findByTemplateCodeAndActiveTrue("CUSTOM_ALERT"))
                .thenReturn(Optional.of(template));

        var rendered = templateRenderer.renderTemplate("CUSTOM_ALERT", Map.of(
                "topic", "Security",
                "message", "Password changed",
                "customerName", "Kamal"
        ));

        assertThat(rendered.channel()).isEqualTo("SMS");
        assertThat(rendered.subject()).isEqualTo("Alert: Security");
        assertThat(rendered.body()).isEqualTo("Notice: Password changed for customer Kamal.");
    }

    @Test
    @DisplayName("Render fallback for PARCEL_OUT_FOR_DELIVERY when template not in database")
    void testRenderFallbackOutForDelivery() {
        when(templateRepository.findByTemplateCodeAndActiveTrue("PARCEL_OUT_FOR_DELIVERY"))
                .thenReturn(Optional.empty());

        var rendered = templateRenderer.renderTemplate("PARCEL_OUT_FOR_DELIVERY", Map.of(
                "trackingNumber", "SD-9999",
                "courierName", "Sunil Perera"
        ));

        assertThat(rendered.channel()).isEqualTo("SMS");
        assertThat(rendered.subject()).isEqualTo("SwiftDrop Out for Delivery");
        assertThat(rendered.body()).contains("SD-9999 is out for delivery with Sunil Perera");
    }

    @Test
    @DisplayName("Render fallback for PARCEL_DELIVERED with EMAIL channel")
    void testRenderFallbackDelivered() {
        when(templateRepository.findByTemplateCodeAndActiveTrue("PARCEL_DELIVERED"))
                .thenReturn(Optional.empty());

        var rendered = templateRenderer.renderTemplate("PARCEL_DELIVERED", Map.of(
                "trackingNumber", "SD-8888",
                "deliveredAt", "14:30 PM"
        ));

        assertThat(rendered.channel()).isEqualTo("EMAIL");
        assertThat(rendered.subject()).isEqualTo("Parcel Delivered - SwiftDrop");
        assertThat(rendered.body()).contains("SD-8888 was successfully delivered at 14:30 PM");
    }

    @Test
    @DisplayName("Render fallback for PARCEL_PICKED_UP")
    void testRenderFallbackPickedUp() {
        when(templateRepository.findByTemplateCodeAndActiveTrue("PARCEL_PICKED_UP"))
                .thenReturn(Optional.empty());

        var rendered = templateRenderer.renderTemplate("PARCEL_PICKED_UP", Map.of(
                "trackingNumber", "SD-7777",
                "courierName", "Amal"
        ));

        assertThat(rendered.channel()).isEqualTo("SMS");
        assertThat(rendered.subject()).isEqualTo("SwiftDrop Parcel Picked Up");
        assertThat(rendered.body()).contains("Courier Amal has picked up parcel SD-7777");
    }

    @Test
    @DisplayName("Render fallback for PARCEL_FAILED")
    void testRenderFallbackFailed() {
        when(templateRepository.findByTemplateCodeAndActiveTrue("PARCEL_FAILED"))
                .thenReturn(Optional.empty());

        var rendered = templateRenderer.renderTemplate("PARCEL_FAILED", Map.of(
                "trackingNumber", "SD-6666",
                "failureReason", "Recipient unreachable"
        ));

        assertThat(rendered.channel()).isEqualTo("SMS");
        assertThat(rendered.subject()).isEqualTo("SwiftDrop Delivery Exception");
        assertThat(rendered.body()).contains("Delivery attempt failed for parcel SD-6666: Recipient unreachable");
    }

    @Test
    @DisplayName("Render default fallback for unknown template code")
    void testRenderFallbackDefault() {
        when(templateRepository.findByTemplateCodeAndActiveTrue("UNKNOWN_EVENT"))
                .thenReturn(Optional.empty());

        var rendered = templateRenderer.renderTemplate("UNKNOWN_EVENT", Map.of(
                "trackingNumber", "SD-5555",
                "status", "IN_CUSTOMS"
        ));

        assertThat(rendered.channel()).isEqualTo("SMS");
        assertThat(rendered.subject()).isEqualTo("SwiftDrop Notification");
        assertThat(rendered.body()).contains("Status update for parcel SD-5555: IN_CUSTOMS");
    }
}
