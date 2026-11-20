package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.util.SensitiveTemplates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Emails a one-time code requested by auth-iam-service (email verification or password reset).
 * Routing key: {@code notification.auth.otp}. The payload carries the IAM user id, never the
 * address; the dispatcher resolves the address from IAM.
 *
 * <p>The payload contains a secret code, so nothing in this class logs it.
 */
@Slf4j
@Component
public class AuthOtpTrigger implements NotificationTrigger {

    public static final String ROUTING_KEY = "notification.auth.otp";

    @Override
    public boolean matches(EventEnvelope envelope) {
        return envelope != null && ROUTING_KEY.equalsIgnoreCase(envelope.routingKey());
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        Optional<UUID> recipientId = envelope.extractRecipientId();
        if (recipientId.isEmpty()) {
            log.warn("OTP event without a valid recipientId; skipping");
            return List.of();
        }

        String templateCode = switch (text(payload, "purpose")) {
            case "EMAIL_VERIFICATION" -> SensitiveTemplates.EMAIL_VERIFICATION;
            case "PASSWORD_RESET" -> SensitiveTemplates.PASSWORD_RESET;
            case "LOGIN_MFA" -> SensitiveTemplates.LOGIN_MFA;
            default -> null;
        };
        String code = text(payload, "code");
        if (templateCode == null || code.isBlank()) {
            log.warn("OTP event for user {} has an unknown purpose or no code; skipping", recipientId.get());
            return List.of();
        }

        String expires = payload.hasNonNull("expiresInMinutes") ? payload.get("expiresInMinutes").asText() : "10";
        return NotificationTrigger.email(recipientId.get(), templateCode,
                Map.of("code", code, "expiresInMinutes", expires), null);
    }

    private static String text(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.get(field).asText().trim() : "";
    }
}
