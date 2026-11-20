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
 * Triggers an EMAIL notification when a parcel is picked up by a courier.
 * Template: {@code PARCEL_PICKED_UP}
 */
@Slf4j
@Component
public class PickedUpTrigger implements NotificationTrigger {

    @Override
    public boolean matches(EventEnvelope envelope) {
        if (envelope == null || envelope.routingKey() == null) {
            return false;
        }
        boolean isParcelStatusEvent = envelope.routingKey().startsWith("parcel.status.changed")
                || envelope.routingKey().startsWith("notification.");
        return isParcelStatusEvent && "PICKED_UP".equalsIgnoreCase(envelope.extractStatus().orElse(""));
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        Optional<UUID> recipientIdOpt = envelope.extractRecipientId();
        if (recipientIdOpt.isEmpty()) {
            log.debug("No recipient ID in PICKED_UP event. Skipping notification.");
            return List.of();
        }

        String trackingNumber = envelope.extractTrackingNumber();
        JsonNode payload = envelope.payload();

        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);
        // Status events do not carry the courier's name; it is shown only when a producer adds it.
        if (payload != null && payload.hasNonNull("courierName") && !payload.get("courierName").asText().isBlank()) {
            params.put("courierName", payload.get("courierName").asText().trim());
        }
        params.put("status", "PICKED_UP");

        return NotificationTrigger.email(recipientIdOpt.get(), "PARCEL_PICKED_UP", params, trackingNumber);
    }
}
