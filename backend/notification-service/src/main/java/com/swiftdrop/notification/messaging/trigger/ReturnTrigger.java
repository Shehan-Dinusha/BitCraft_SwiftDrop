package com.swiftdrop.notification.messaging.trigger;

import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.util.DisplayText;
import com.swiftdrop.notification.util.InboxAudience;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells the sender that their parcel is coming back, and that they have it back.
 * Templates: {@code PARCEL_RETURNING}, {@code PARCEL_RETURNED}
 */
@Component
public class ReturnTrigger implements NotificationTrigger {

    public static final String MERCHANT_TYPE = "MERCHANT_RETURN";

    @Override
    public boolean matches(EventEnvelope envelope) {
        if (envelope == null || envelope.routingKey() == null) {
            return false;
        }
        boolean isParcelStatusEvent = envelope.routingKey().startsWith("parcel.status.changed")
                || envelope.routingKey().startsWith("notification.");
        // Every step of the way back reports RETURNING; only the first one is news.
        return isParcelStatusEvent && envelope.statusChanged() && (isReturning(envelope) || isReturned(envelope));
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        UUID recipient = envelope.extractRecipientId().orElse(null);
        if (recipient == null) {
            return List.of();
        }
        String trackingNumber = envelope.extractTrackingNumber();
        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);
        if (isReturned(envelope)) {
            String at = envelope.payload().hasNonNull("timestamp")
                    ? DisplayText.when(envelope.payload().get("timestamp").asText()) : null;
            params.put("returnedAt", at != null ? at : "today");
        }
        return NotificationTrigger.email(recipient, isReturned(envelope) ? "PARCEL_RETURNED" : "PARCEL_RETURNING",
                params, trackingNumber);
    }

    /** The consumer writes the row only for a known merchant company. */
    @Override
    public List<TeamNotification> teamNotifications(EventEnvelope envelope) {
        String trackingNumber = envelope.extractTrackingNumber();
        String parcel = trackingNumber == null ? "A parcel" : "Parcel " + trackingNumber;
        boolean returned = isReturned(envelope);
        return envelope.extractRecipientId().map(InboxAudience::merchant)
                .map(audience -> List.of(new TeamNotification(audience, MERCHANT_TYPE,
                        returned ? "Parcel returned" : "Parcel coming back",
                        parcel + (returned ? " was handed back to you." : " is on its way back to you. The return is free."),
                        trackingNumber)))
                .orElse(List.of());
    }

    private static boolean isReturning(EventEnvelope envelope) {
        return "RETURNING".equalsIgnoreCase(envelope.extractStatus().orElse(""));
    }

    private static boolean isReturned(EventEnvelope envelope) {
        return "RETURNED_TO_SENDER".equalsIgnoreCase(envelope.extractStatus().orElse(""));
    }
}
