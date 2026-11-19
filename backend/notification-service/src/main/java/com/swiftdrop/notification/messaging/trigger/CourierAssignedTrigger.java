package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.util.DisplayText;
import com.swiftdrop.notification.util.InboxAudience;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A courier was assigned to a parcel ({@code dispatch.courier.assigned}).
 * <ul>
 *   <li>The courier gets an email and an in-app notification ({@code COURIER_ASSIGNED}).</li>
 *   <li>For merchant orders, the merchant company's team inbox gets "courier assigned".</li>
 * </ul>
 * {@code courierId} is the courier's IAM user id, so the email goes to their account address.
 */
@Slf4j
@Component
public class CourierAssignedTrigger implements NotificationTrigger {

    public static final String ROUTING_KEY = "dispatch.courier.assigned";
    public static final String TEMPLATE_CODE = "COURIER_ASSIGNED";
    public static final String MERCHANT_TYPE = "MERCHANT_COURIER_ASSIGNED";

    @Override
    public boolean matches(EventEnvelope envelope) {
        return envelope != null && ROUTING_KEY.equalsIgnoreCase(envelope.routingKey());
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        UUID courierId = uuid(payload, "courierId");
        if (courierId == null) {
            log.debug("Courier assignment without a courierId; no courier notification");
            return List.of();
        }
        String trackingNumber = envelope.extractTrackingNumber();
        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);
        put(params, "pickupDistrict", DisplayText.title(text(payload, "pickupDistrict")));
        put(params, "deliveryDistrict", DisplayText.title(text(payload, "deliveryDistrict")));
        put(params, "estimatedPickupMinutes", text(payload, "estimatedPickupMinutes"));
        return NotificationTrigger.email(courierId, TEMPLATE_CODE, params, trackingNumber);
    }

    @Override
    public List<TeamNotification> teamNotifications(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        String audience = InboxAudience.merchant(uuid(payload, "merchantId"));
        if (audience == null) {
            return List.of();
        }
        String trackingNumber = envelope.extractTrackingNumber();
        String courier = text(payload, "courierName");
        String who = courier == null ? "A courier" : courier;
        String parcel = trackingNumber == null ? "your parcel" : "parcel " + trackingNumber;
        String body = who + " has been assigned to collect " + parcel + route(payload) + ".";
        return List.of(new TeamNotification(audience, MERCHANT_TYPE, "Courier assigned", body, trackingNumber));
    }

    static String route(JsonNode payload) {
        String from = DisplayText.title(text(payload, "pickupDistrict"));
        String to = DisplayText.title(text(payload, "deliveryDistrict"));
        return from != null && to != null ? " (" + from + " → " + to + ")" : "";
    }

    private static void put(Map<String, String> params, String key, String value) {
        if (value != null) {
            params.put(key, value);
        }
    }

    static String text(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            return null;
        }
        String value = payload.get(field).asText().trim();
        return value.isEmpty() ? null : value;
    }

    static UUID uuid(JsonNode payload, String field) {
        String value = text(payload, field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
