package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.service.email.EmailHtmlRenderer;
import com.swiftdrop.notification.util.SensitiveTemplates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffInviteTriggerTest {

    private static final String LINK = "https://app.example.test/invite#token=" + "a".repeat(43);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StaffInviteTrigger trigger = new StaffInviteTrigger();
    private final EmailHtmlRenderer renderer = new EmailHtmlRenderer();

    private EventEnvelope envelope(String json) {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("notification.auth.invite");
        return EventEnvelope.from(new Message(json.getBytes(StandardCharsets.UTF_8), props), objectMapper).orElseThrow();
    }

    @Test
    @DisplayName("Builds a STAFF_INVITE email carrying the link and expiry")
    void buildsInviteEmail() {
        UUID recipient = UUID.randomUUID();
        EventEnvelope envelope = envelope("""
                {"eventId":"%s","recipientId":"%s","role":"COURIER","inviteUrl":"%s","expiresInHours":72}
                """.formatted(UUID.randomUUID(), recipient, LINK));

        assertThat(trigger.matches(envelope)).isTrue();
        List<DispatchNotificationRequest> requests = trigger.build(envelope);

        assertThat(requests).hasSize(1);
        DispatchNotificationRequest request = requests.getFirst();
        assertThat(request.recipientId()).isEqualTo(recipient);
        assertThat(request.channel()).isEqualTo("EMAIL");
        assertThat(request.templateCode()).isEqualTo(SensitiveTemplates.STAFF_INVITE);
        assertThat(request.templateParams()).containsEntry("inviteUrl", LINK).containsEntry("expiresInHours", "72");
    }

    @Test
    @DisplayName("An event without a link sends nothing")
    void skipsEventWithoutLink() {
        EventEnvelope envelope = envelope("""
                {"eventId":"%s","recipientId":"%s","role":"COURIER"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(trigger.build(envelope)).isEmpty();
    }

    @Test
    @DisplayName("The invite is a redacted security email")
    void inviteIsSensitive() {
        assertThat(SensitiveTemplates.isSensitive(SensitiveTemplates.STAFF_INVITE)).isTrue();
        assertThat(SensitiveTemplates.isSecurity(SensitiveTemplates.STAFF_INVITE)).isTrue();
    }

    @Test
    @DisplayName("The email shows a button with the invite link and its expiry")
    void rendersButton() {
        String html = renderer.renderHtml(SensitiveTemplates.STAFF_INVITE, "Set up your SwiftDrop account", "body",
                null, Map.of("inviteUrl", LINK, "expiresInHours", "72"));

        assertThat(html).contains("href=\"" + LINK + "\"").contains("Set up my account").contains("72 hours");
    }

    @Test
    @DisplayName("A link that is not a SwiftDrop invite link is never rendered")
    void dropsForeignLink() {
        String html = renderer.renderHtml(SensitiveTemplates.STAFF_INVITE, "Set up your SwiftDrop account", "body",
                null, Map.of("inviteUrl", "javascript:alert(1)//invite#token=" + "a".repeat(43), "expiresInHours", "72"));

        assertThat(html).doesNotContain("javascript:").doesNotContain("Set up my account");
    }
}
