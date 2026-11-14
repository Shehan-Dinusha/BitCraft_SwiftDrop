package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthOtpTriggerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthOtpTrigger trigger = new AuthOtpTrigger();

    private EventEnvelope envelope(String routingKey, String json) {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey(routingKey);
        return EventEnvelope.from(new Message(json.getBytes(StandardCharsets.UTF_8), props), objectMapper).orElseThrow();
    }

    private String payload(UUID recipient, String purpose, String code) {
        return """
                {"eventId":"%s","recipientId":"%s","purpose":"%s","code":"%s","expiresInMinutes":10}
                """.formatted(UUID.randomUUID(), recipient, purpose, code);
    }

    @Test
    @DisplayName("Builds an EMAIL verification request carrying the code and expiry, with no tracking number")
    void buildsEmailVerification() {
        UUID recipient = UUID.randomUUID();
        EventEnvelope envelope = envelope("notification.auth.otp", payload(recipient, "EMAIL_VERIFICATION", "482913"));

        assertThat(trigger.matches(envelope)).isTrue();
        List<DispatchNotificationRequest> requests = trigger.build(envelope);

        assertThat(requests).hasSize(1);
        DispatchNotificationRequest request = requests.getFirst();
        assertThat(request.recipientId()).isEqualTo(recipient);
        assertThat(request.channel()).isEqualTo("EMAIL");
        assertThat(request.templateCode()).isEqualTo("EMAIL_VERIFICATION");
        assertThat(request.templateParams()).containsEntry("code", "482913").containsEntry("expiresInMinutes", "10");
        assertThat(request.referenceId()).isNull();
    }

    @Test
    @DisplayName("Maps the password-reset purpose to its own template")
    void buildsPasswordReset() {
        EventEnvelope envelope = envelope("notification.auth.otp", payload(UUID.randomUUID(), "PASSWORD_RESET", "111222"));

        assertThat(trigger.build(envelope)).singleElement()
                .satisfies(request -> assertThat(request.templateCode()).isEqualTo("PASSWORD_RESET"));
    }

    @Test
    @DisplayName("Ignores other routing keys and does not collide with parcel triggers")
    void ignoresOtherEvents() {
        EventEnvelope otp = envelope("notification.auth.otp", payload(UUID.randomUUID(), "EMAIL_VERIFICATION", "482913"));
        EventEnvelope parcel = envelope("parcel.status.changed.picked_up", "{\"customerId\":\"" + UUID.randomUUID() + "\"}");

        assertThat(trigger.matches(parcel)).isFalse();
        assertThat(new PickedUpTrigger().matches(otp)).isFalse();
        assertThat(new OrderCreatedTrigger().matches(otp)).isFalse();
    }

    @Test
    @DisplayName("Skips events with an unknown purpose, a missing code or no recipient")
    void skipsMalformedEvents() {
        UUID recipient = UUID.randomUUID();
        assertThat(trigger.build(envelope("notification.auth.otp", payload(recipient, "SOMETHING_ELSE", "482913")))).isEmpty();
        assertThat(trigger.build(envelope("notification.auth.otp", payload(recipient, "EMAIL_VERIFICATION", "")))).isEmpty();
        assertThat(trigger.build(envelope("notification.auth.otp",
                "{\"purpose\":\"EMAIL_VERIFICATION\",\"code\":\"482913\"}"))).isEmpty();
    }
}
