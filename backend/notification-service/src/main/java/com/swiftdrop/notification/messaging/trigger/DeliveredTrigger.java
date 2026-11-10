package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
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
        String deliveredAt = "just now";
        JsonNode payload = envelope.payload();
        if (payload != null && payload.hasNonNull("deliveredAt")) {
            deliveredAt = payload.get("deliveredAt").asText();
        } else if (payload != null && payload.hasNonNull("timestamp")) {
            deliveredAt = payload.get("timestamp").asText();
        }

        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);
        params.put("deliveredAt", deliveredAt);
        params.put("status", "DELIVERED");

        return List.of(new DispatchNotificationRequest(
                recipientIdOpt.get(),
                "EMAIL",
                "PARCEL_DELIVERED",
                null,
                params,
                trackingNumber
        ));
    }
}
