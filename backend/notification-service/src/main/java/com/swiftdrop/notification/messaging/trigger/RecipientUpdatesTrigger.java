package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.notification.client.ContactClient;
import com.swiftdrop.notification.client.OrderRecipientClient;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.RecipientContact;
import com.swiftdrop.notification.service.RecipientEmailStopService;
import com.swiftdrop.notification.util.DisplayText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Emails the recipient about their parcel, from booking to the end, at the address the sender entered. The
 * recipient has no account, so the email is all they get: no inbox row, no device push, no settings.
 *
 * <ul>
 *   <li>{@code order.created}: a parcel is on its way to you, from whom, and the promised day</li>
 *   <li>picked up, out for delivery (the courier is bringing it today), delivered</li>
 *   <li>a failed attempt or a failed delivery, the return to the sender, and a cancellation</li>
 * </ul>
 *
 * Only a change of public status is news (the hub steps all read IN_TRANSIT and send nothing). Events carry no
 * email address, so it is read from the order service each time. A sender who entered their own email as
 * the recipient's gets only the sender's mail. Each email has a "Stop these emails" link for this parcel; once
 * the recipient uses it, nothing more is sent to them (the sender's mail goes on).
 */
@Slf4j
@Component
public class RecipientUpdatesTrigger implements NotificationTrigger {

    public static final String CREATED = "RECIPIENT_PARCEL_CREATED";
    public static final String PICKED_UP = "RECIPIENT_PICKED_UP";
    public static final String OUT_FOR_DELIVERY = "RECIPIENT_OUT_FOR_DELIVERY";
    public static final String DELIVERED = "RECIPIENT_DELIVERED";
    public static final String FAILED = "RECIPIENT_FAILED";
    public static final String RETURNING = "RECIPIENT_RETURNING";
    public static final String CANCELLED = "RECIPIENT_CANCELLED";

    private static final Map<String, String> BY_STATUS = Map.of(
            "PICKED_UP", PICKED_UP,
            "OUT_FOR_DELIVERY", OUT_FOR_DELIVERY,
            "DELIVERED", DELIVERED,
            "DELIVERY_ATTEMPTED", FAILED,
            "FAILED", FAILED,
            "RETURNING", RETURNING);

    private final OrderRecipientClient orders;
    private final ContactClient accounts;
    private final RecipientEmailStopService stops;

    public RecipientUpdatesTrigger(OrderRecipientClient orders, ContactClient accounts, RecipientEmailStopService stops) {
        this.orders = orders;
        this.accounts = accounts;
        this.stops = stops;
    }

    @Override
    public boolean matches(EventEnvelope envelope) {
        return template(envelope) != null;
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        String template = template(envelope);
        UUID orderId = uuid(envelope.payload(), "orderId");
        if (template == null || orderId == null) {
            return List.of();
        }
        if (stops.isStopped(orderId)) {
            log.debug("The recipient of order {} stopped its emails", orderId);
            return List.of();
        }
        Optional<OrderRecipientClient.RecipientContact> found = orders.lookup(orderId);
        if (found.isEmpty() || found.get().email() == null || !found.get().email().contains("@")) {
            return List.of(); // the sender left the email out (it is optional), or the order is unknown
        }
        OrderRecipientClient.RecipientContact contact = found.get();
        if (isSendersOwnEmail(envelope, contact.email())) {
            log.debug("Recipient email of order {} is the sender's own; the sender's mail covers it", orderId);
            return List.of();
        }

        String trackingNumber = envelope.extractTrackingNumber() != null
                ? envelope.extractTrackingNumber() : contact.trackingNumber();
        Map<String, String> params = new HashMap<>();
        put(params, "trackingNumber", trackingNumber);
        put(params, "recipientName", contact.recipientName());
        put(params, "senderName", contact.senderName() != null ? contact.senderName() : "The sender");
        params.put("stopToken", stops.tokenFor(orderId));
        switch (template) {
            case CREATED -> {
                put(params, "pickupDistrict", text(envelope.payload(), "pickupDistrict"));
                put(params, "deliveryDistrict", text(envelope.payload(), "deliveryDistrict"));
                put(params, "speed", text(envelope.payload(), "speed"));
                put(params, "deliverBy", DisplayText.day(contact.deliverBy()));
            }
            case DELIVERED -> {
                String at = DisplayText.when(text(envelope.payload(), "timestamp"));
                params.put("deliveredAt", at != null ? at : "today");
            }
            case FAILED -> {
                String code = text(envelope.payload(), "failureReason");
                params.put("failureReason", DisplayText.failureReason(code));
                params.put("nextStep", nextStep(envelope, code));
            }
            default -> {
                // The tracking number and names are all these need.
            }
        }
        // A stable id per order's recipient keeps their emails together in the log; it is nobody's account.
        UUID recipientId = UUID.nameUUIDFromBytes(("recipient:" + orderId).getBytes(StandardCharsets.UTF_8));
        return List.of(new DispatchNotificationRequest(recipientId, "EMAIL", template, contact.email(), params,
                trackingNumber));
    }

    private static String template(EventEnvelope envelope) {
        if (envelope == null || envelope.routingKey() == null) {
            return null;
        }
        String key = envelope.routingKey().toLowerCase(Locale.ROOT);
        if (key.equals("order.created")) {
            return CREATED;
        }
        if (key.equals("order.cancelled")) {
            return CANCELLED;
        }
        if (!key.startsWith("parcel.status.changed") || !envelope.statusChanged()) {
            return null;
        }
        return BY_STATUS.get(envelope.extractStatus().orElse("").toUpperCase(Locale.ROOT));
    }

    private static String nextStep(EventEnvelope envelope, String code) {
        if (!"DELIVERY_ATTEMPTED".equalsIgnoreCase(envelope.extractStatus().orElse(""))) {
            return "The sender has been told. Please contact them about the next steps.";
        }
        return refusal(code)
                ? "It goes back to the sender."
                : "We will try again, or return it to the sender. Please keep your phone with you.";
    }

    private static boolean refusal(String code) {
        try {
            return code != null && DeliveryFailureReason.valueOf(code.trim().toUpperCase(Locale.ROOT)).isRefusal();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isSendersOwnEmail(EventEnvelope envelope, String email) {
        UUID sender = uuid(envelope.payload(), "customerId");
        if (sender == null || accounts == null) {
            return false;
        }
        return accounts.lookupContact(sender)
                .map(RecipientContact::email)
                .filter(own -> own.trim().equalsIgnoreCase(email.trim()))
                .isPresent();
    }

    private static void put(Map<String, String> params, String key, String value) {
        if (value != null) {
            params.put(key, value);
        }
    }

    private static String text(JsonNode payload, String field) {
        return CourierAssignedTrigger.text(payload, field);
    }

    private static UUID uuid(JsonNode payload, String field) {
        return CourierAssignedTrigger.uuid(payload, field);
    }
}
