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
 * Emails a staff invite link requested by auth-iam-service when a dispatcher registers a courier (or an
 * admin creates a dispatcher). Routing key: {@code notification.auth.invite}. The payload carries the
 * IAM user id, never the address; the dispatcher resolves the address from IAM.
 *
 * <p>The link signs the person in, so nothing in this class logs it.
 */
@Slf4j
@Component
public class StaffInviteTrigger implements NotificationTrigger {

    public static final String ROUTING_KEY = "notification.auth.invite";

    @Override
    public boolean matches(EventEnvelope envelope) {
        return envelope != null && ROUTING_KEY.equalsIgnoreCase(envelope.routingKey());
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        Optional<UUID> recipientId = envelope.extractRecipientId();
        if (recipientId.isEmpty()) {
            log.warn("Staff invite event without a valid recipientId; skipping");
            return List.of();
        }
        String inviteUrl = payload.hasNonNull("inviteUrl") ? payload.get("inviteUrl").asText().trim() : "";
        if (inviteUrl.isBlank()) {
            log.warn("Staff invite event for user {} has no link; skipping", recipientId.get());
            return List.of();
        }
        String hours = payload.hasNonNull("expiresInHours") ? payload.get("expiresInHours").asText() : "72";
        return NotificationTrigger.email(recipientId.get(), SensitiveTemplates.STAFF_INVITE,
                Map.of("inviteUrl", inviteUrl, "expiresInHours", hours), null);
    }
}
