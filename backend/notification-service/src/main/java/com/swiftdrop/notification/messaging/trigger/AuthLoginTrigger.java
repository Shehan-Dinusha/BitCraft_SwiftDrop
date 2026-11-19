package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.util.DisplayText;
import com.swiftdrop.notification.util.SensitiveTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Emails a security alert for a new sign-in or a wrong-password attempt (routing key
 * {@code notification.auth.login}, published by auth-iam-service). The dispatcher sends it only to the
 * account's registered address and limits failed-sign-in alerts to one per 15 minutes.
 */
@Slf4j
@Component
public class AuthLoginTrigger implements NotificationTrigger {

    public static final String ROUTING_KEY = "notification.auth.login";

    @Override
    public boolean matches(EventEnvelope envelope) {
        return envelope != null && ROUTING_KEY.equalsIgnoreCase(envelope.routingKey());
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        Optional<UUID> recipientId = envelope.extractRecipientId();
        String outcome = text(payload, "outcome");
        String template = switch (outcome) {
            case "SUCCESS" -> SensitiveTemplates.LOGIN_SUCCESS;
            case "FAILED" -> SensitiveTemplates.LOGIN_FAILED;
            default -> null;
        };
        if (recipientId.isEmpty() || template == null) {
            log.warn("Sign-in event without a recipient or with an unknown outcome; skipping");
            return List.of();
        }

        Map<String, String> params = new HashMap<>();
        String when = DisplayText.when(text(payload, "occurredAt"));
        params.put("occurredAt", when != null ? when : "just now");
        params.put("device", DisplayText.device(text(payload, "userAgent")));
        String ip = DisplayText.maskIp(text(payload, "ipAddress"));
        if (ip != null) {
            params.put("ipAddress", ip);
        }
        return NotificationTrigger.email(recipientId.get(), template, params, null);
    }

    private static String text(JsonNode payload, String field) {
        return payload != null && payload.hasNonNull(field) ? payload.get(field).asText().trim() : "";
    }
}
