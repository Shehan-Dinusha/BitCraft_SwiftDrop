package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.util.DisplayText;
import com.swiftdrop.notification.util.InboxAudience;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    public static final String OPERATIONS_TYPE = "OPS_DELIVERY_FAILED";
    public static final String MERCHANT_TYPE = "MERCHANT_DELIVERY_FAILED";

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
        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);
        params.put("failureReason", reason(envelope));
        params.put("status", "FAILED");

        return NotificationTrigger.email(recipientIdOpt.get(), "PARCEL_FAILED", params, trackingNumber);
    }

    /**
     * A failed delivery is final in dispatch, so someone has to decide what happens next: the operations
     * team always hears about it, and for a merchant order so does the merchant (the order's customerId is
     * the merchant id; the consumer writes a merchant row only for a known merchant company).
     */
    @Override
    public List<TeamNotification> teamNotifications(EventEnvelope envelope) {
        String trackingNumber = envelope.extractTrackingNumber();
        String parcel = trackingNumber == null ? "A parcel" : "Parcel " + trackingNumber;
        String reason = reason(envelope);
        List<TeamNotification> rows = new ArrayList<>();
        rows.add(new TeamNotification(InboxAudience.OPERATIONS, OPERATIONS_TYPE, "Delivery failed",
                parcel + " could not be delivered: " + reason + ". Arrange a new attempt or a return to the sender.",
                trackingNumber));
        envelope.extractRecipientId().map(InboxAudience::merchant).ifPresent(audience ->
                rows.add(new TeamNotification(audience, MERCHANT_TYPE, "Delivery failed",
                        parcel + " could not be delivered: " + reason + ". SwiftDrop operations will arrange the next step.",
                        trackingNumber)));
        return rows;
    }

    private static String reason(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        String code = payload != null && payload.hasNonNull("failureReason") ? payload.get("failureReason").asText() : null;
        return DisplayText.failureReason(code);
    }
}
