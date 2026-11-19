package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.util.InboxAudience;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * No courier could be assigned to an order ({@code dispatch.failed}). The operations team
 * (every dispatcher and system admin) gets an in-app alert so someone can assign the parcel manually.
 * No email: this is an operational alert, not a customer message.
 */
@Component
public class DispatchFailedTrigger implements NotificationTrigger {

    public static final String ROUTING_KEY = "dispatch.failed";
    public static final String TYPE = "DISPATCH_FAILED";

    @Override
    public boolean matches(EventEnvelope envelope) {
        return envelope != null && ROUTING_KEY.equalsIgnoreCase(envelope.routingKey());
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        return List.of();
    }

    @Override
    public List<TeamNotification> teamNotifications(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        // A failure that dispatch retries by itself needs nobody: only the final one is worth an alert.
        if (payload == null || payload.path("retryable").asBoolean(false)) {
            return List.of();
        }
        String orderId = CourierAssignedTrigger.text(payload, "orderId");
        String reason = CourierAssignedTrigger.text(payload, "failureReason");
        String attempts = CourierAssignedTrigger.text(payload, "attemptCount");

        StringBuilder body = new StringBuilder("No courier could be assigned to order ")
                .append(orderId == null ? "(unknown)" : shortId(orderId));
        if (reason != null) {
            body.append(": ").append(reason);
        }
        body.append('.');
        if (attempts != null) {
            body.append(" Attempt ").append(attempts).append('.');
        }
        body.append(" Please assign a courier manually.");

        return List.of(new TeamNotification(InboxAudience.OPERATIONS, TYPE, "Dispatch needs attention",
                body.toString(), envelope.extractTrackingNumber()));
    }

    /** First 8 characters of a UUID, as shown in the operations screens. */
    private static String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8).toUpperCase() : id.toUpperCase();
    }
}
