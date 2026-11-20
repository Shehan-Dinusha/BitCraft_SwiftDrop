package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.util.DisplayText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Triggers an EMAIL notification when a parcel is successfully delivered.
 * Template: {@code PARCEL_DELIVERED}
 */
@Slf4j
@Component
public class DeliveredTrigger implements NotificationTrigger {

    @Override
    public boolean matches(EventEnvelope envelope) {
        if (envelope == null || envelope.routingKey() == null) {
            return false;
        }
        boolean isParcelStatusEvent = envelope.routingKey().startsWith("parcel.status.changed")
                || envelope.routingKey().startsWith("notification.");
        return isParcelStatusEvent && "DELIVERED".equalsIgnoreCase(envelope.extractStatus().orElse(""));
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        Optional<UUID> recipientIdOpt = envelope.extractRecipientId();
        if (recipientIdOpt.isEmpty()) {
            log.debug("No recipient ID in DELIVERED event. Skipping notification.");
            return List.of();
        }

        String trackingNumber = envelope.extractTrackingNumber();
        JsonNode payload = envelope.payload();
        String raw = payload == null ? null
                : payload.hasNonNull("deliveredAt") ? payload.get("deliveredAt").asText()
                : payload.hasNonNull("timestamp") ? payload.get("timestamp").asText()
                : null;
        // "19 Nov 2026, 9:26 p.m." (Sri Lanka time), never a raw ISO timestamp.
        String formatted = DisplayText.when(raw);
        String deliveredAt = formatted != null ? formatted : "today";

        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);
        params.put("deliveredAt", deliveredAt);
        params.put("status", "DELIVERED");

        return NotificationTrigger.email(recipientIdOpt.get(), "PARCEL_DELIVERED", params, trackingNumber);
    }
}
