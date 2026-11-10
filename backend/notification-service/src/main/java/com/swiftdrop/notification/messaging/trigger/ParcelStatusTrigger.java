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
 * Handles parcel status changes by evaluating the event status and dispatching
 * the corresponding SMS or EMAIL notification based on seeded templates.
 */
@Slf4j
@Component
public class ParcelStatusTrigger implements NotificationTrigger {

    @Override
    public boolean matches(EventEnvelope envelope) {
        if (envelope == null || envelope.routingKey() == null) {
            return false;
        }
        return envelope.routingKey().startsWith("parcel.status.changed")
                || envelope.routingKey().startsWith("notification.");
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        Optional<UUID> recipientIdOpt = envelope.extractRecipientId();
        if (recipientIdOpt.isEmpty()) {
            log.debug("No recipient ID found in event with key {}. Skipping notification.", envelope.routingKey());
            return List.of();
        }

        UUID recipientId = recipientIdOpt.get();
        String trackingNumber = envelope.extractTrackingNumber();
        String status = envelope.extractStatus().orElse("CREATED");

        String templateCode = resolveTemplateCode(status);
        String channel = "DELIVERED".equalsIgnoreCase(status) ? "EMAIL" : "SMS";

        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);
        params.put("status", status);
        params.put("deliveredAt", "just now");

        JsonNode payload = envelope.payload();
        if (payload != null && payload.hasNonNull("failureReason")) {
            params.put("failureReason", payload.get("failureReason").asText());
        }

        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId,
                channel,
                templateCode,
                null,
                params,
                trackingNumber
        );

        return List.of(request);
    }

    private String resolveTemplateCode(String status) {
        return switch (status.toUpperCase()) {
            case "PICKED_UP" -> "PARCEL_PICKED_UP";
            case "OUT_FOR_DELIVERY" -> "PARCEL_OUT_FOR_DELIVERY";
            case "DELIVERED" -> "PARCEL_DELIVERED";
            case "FAILED" -> "PARCEL_FAILED";
            default -> "PARCEL_CREATED";
        };
    }
}
