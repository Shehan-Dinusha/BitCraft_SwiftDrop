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
 * Triggers an EMAIL alert when a delivery attempt fails (DELIVERY_ATTEMPTED: it is tried again or returned)
 * or the delivery is over (FAILED: lost, damaged or never collected).
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
        return isParcelStatusEvent && envelope.statusChanged() && (isAttempt(envelope) || "FAILED".equalsIgnoreCase(envelope.extractStatus().orElse("")));
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
        params.put("nextStep", isAttempt(envelope)
                ? "It goes back to our hub, where we try again or return it to you."
                : "Our operations team has been told.");
        params.put("status", "FAILED");

        return NotificationTrigger.email(recipientIdOpt.get(), "PARCEL_FAILED", params, trackingNumber);
    }

    /**
     * Someone has to decide what happens next: the operations team always hears about it, and for a merchant order so does the merchant (the order's customerId is
     * the merchant id; the consumer writes a merchant row only for a known merchant company).
     */
    @Override
    public List<TeamNotification> teamNotifications(EventEnvelope envelope) {
        String trackingNumber = envelope.extractTrackingNumber();
        String parcel = trackingNumber == null ? "A parcel" : "Parcel " + trackingNumber;
        String reason = reason(envelope);
        List<TeamNotification> rows = new ArrayList<>();
        rows.add(new TeamNotification(InboxAudience.OPERATIONS, OPERATIONS_TYPE, "Delivery failed",
                parcel + " could not be delivered: " + reason + "."
                + (isAttempt(envelope) ? " It comes back to the hub: try again or return it to the sender." : ""),
                trackingNumber));
        envelope.extractRecipientId().map(InboxAudience::merchant).ifPresent(audience ->
                rows.add(new TeamNotification(audience, MERCHANT_TYPE, "Delivery failed",
                        parcel + " could not be delivered: " + reason + "."
                        + (isAttempt(envelope) ? " We try again or return it to you." : " SwiftDrop operations have been told."),
                        trackingNumber)));
        return rows;
    }

    private static boolean isAttempt(EventEnvelope envelope) {
        return "DELIVERY_ATTEMPTED".equalsIgnoreCase(envelope.extractStatus().orElse(""));
    }

    private static String reason(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        String code = payload != null && payload.hasNonNull("failureReason") ? payload.get("failureReason").asText() : null;
        return DisplayText.failureReason(code);
    }
}
