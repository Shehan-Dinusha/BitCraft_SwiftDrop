package com.swiftdrop.notification.messaging.trigger;

import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells the sender a courier is coming to collect their parcel, so it is packed and the fee is ready in cash.
 * It follows the status event (CREATED to PICKUP_ASSIGNED), not {@code dispatch.courier.assigned}, which is
 * also published for the hub legs and for every reassignment; a reassignment keeps the status, so it sends
 * nothing. Merchant orders are left to the merchant's team inbox ({@link CourierAssignedTrigger}).
 * Template: {@code PARCEL_PICKUP_ASSIGNED}
 */
@Component
public class PickupAssignedTrigger implements NotificationTrigger {

    public static final String TEMPLATE_CODE = "PARCEL_PICKUP_ASSIGNED";

    @Override
    public boolean matches(EventEnvelope envelope) {
        if (envelope == null || envelope.routingKey() == null) {
            return false;
        }
        return envelope.routingKey().startsWith("parcel.status.changed")
                && envelope.statusChanged()
                && "PICKUP_ASSIGNED".equalsIgnoreCase(envelope.extractStatus().orElse(""));
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        UUID sender = envelope.extractRecipientId().orElse(null);
        if (sender == null) {
            return List.of();
        }
        String trackingNumber = envelope.extractTrackingNumber();
        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);
        params.put("status", "PICKUP_ASSIGNED");
        return NotificationTrigger.email(sender, TEMPLATE_CODE, params, trackingNumber);
    }
}
