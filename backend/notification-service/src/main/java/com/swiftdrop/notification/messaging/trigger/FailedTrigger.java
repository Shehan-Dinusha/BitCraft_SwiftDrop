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
 * Triggers an EMAIL alert when a delivery attempt fails.
 * Template: {@code PARCEL_FAILED}
 */
@Slf4j
@Component
public class FailedTrigger implements NotificationTrigger {

    @Override
    public boolean matches(EventEnvelope envelope) {
        if (envelope == null || envelope.routingKey() == null) {
            return false;
        }
        boolean isParcelStatusEvent = envelope.routingKey().startsWith("parcel.status.changed")
                || envelope.routingKey().startsWith("notification.");
        return isParcelStatusEvent && "FAILED".equalsIgnoreCase(envelope.extractStatus().orElse(""));
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        Optional<UUID> recipientIdOpt = envelope.extractRecipientId();
        if (recipientIdOpt.isEmpty()) {
            log.debug("No recipient ID in FAILED event. Skipping notification.");
            return List.of();
        }

        String trackingNumber = envelope.extractTrackingNumber();
        String failureReason = "Delivery attempt unsuccessful";
        JsonNode payload = envelope.payload();
        if (payload != null && payload.hasNonNull("failureReason")) {
            failureReason = payload.get("failureReason").asText();
        }

        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);
        params.put("failureReason", failureReason);
        params.put("status", "FAILED");

        return NotificationTrigger.email(recipientIdOpt.get(), "PARCEL_FAILED", params, trackingNumber);
    }
}
