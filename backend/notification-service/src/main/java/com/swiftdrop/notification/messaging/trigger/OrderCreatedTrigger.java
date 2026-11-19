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
import java.util.Optional;
import java.util.UUID;

/**
 * Triggers an EMAIL confirmation when a new customer order is placed.
 * Routing key: {@code order.created}
 * Template: {@code PARCEL_CREATED}
 */
@Slf4j
@Component
public class OrderCreatedTrigger implements NotificationTrigger {

    public static final String ROUTING_KEY = "order.created";
    public static final String TEMPLATE_CODE = "PARCEL_CREATED";
    public static final String MERCHANT_TYPE = "MERCHANT_ORDER_CREATED";

    @Override
    public boolean matches(EventEnvelope envelope) {
        if (envelope == null || envelope.routingKey() == null) {
            return false;
        }
        return ROUTING_KEY.equalsIgnoreCase(envelope.routingKey());
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        Optional<UUID> recipientIdOpt = envelope.extractRecipientId();
        if (recipientIdOpt.isEmpty()) {
            log.debug("No customer/recipient ID found in order.created event. Skipping notification.");
            return List.of();
        }

        String trackingNumber = envelope.extractTrackingNumber();
        JsonNode payload = envelope.payload();

        Map<String, String> params = new HashMap<>();
        params.put("trackingNumber", trackingNumber);

        if (payload != null) {
            if (payload.hasNonNull("pickupDistrict")) {
                params.put("pickupDistrict", payload.get("pickupDistrict").asText());
            }
            if (payload.hasNonNull("deliveryDistrict")) {
                params.put("deliveryDistrict", payload.get("deliveryDistrict").asText());
            }
            if (payload.hasNonNull("speed")) {
                params.put("speed", payload.get("speed").asText());
            }
            if (payload.hasNonNull("orderId")) {
                params.put("orderId", payload.get("orderId").asText());
            }
        }

        return NotificationTrigger.email(recipientIdOpt.get(), TEMPLATE_CODE, params, trackingNumber);
    }

    /** Orders placed for a merchant also appear in that merchant company's team inbox. */
    @Override
    public List<TeamNotification> teamNotifications(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        String audience = InboxAudience.merchant(CourierAssignedTrigger.uuid(payload, "merchantId"));
        if (audience == null) {
            return List.of();
        }
        String trackingNumber = envelope.extractTrackingNumber();
        String speed = DisplayText.title(CourierAssignedTrigger.text(payload, "speed"));
        String body = "Order " + (trackingNumber == null ? "" : trackingNumber + " ") + "was created"
                + CourierAssignedTrigger.route(payload) + (speed == null ? "" : ", " + speed + " delivery") + ".";
        return List.of(new TeamNotification(audience, MERCHANT_TYPE, "New order received", body, trackingNumber));
    }
}
