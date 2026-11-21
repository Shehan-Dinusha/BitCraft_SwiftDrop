package com.swiftdrop.notification.messaging.trigger;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.util.InboxAudience;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Merchant business applications ({@code notification.merchant.application}).
 * <ul>
 *   <li>SUBMITTED: the dispatcher of the business's district gets an in-app alert to review it.</li>
 *   <li>APPROVED / REJECTED: the owner is emailed the decision (and, for a rejection, the reason).</li>
 * </ul>
 */
@Slf4j
@Component
public class MerchantApplicationTrigger implements NotificationTrigger {

    public static final String ROUTING_KEY = "notification.merchant.application";
    public static final String TYPE_SUBMITTED = "MERCHANT_APPLICATION";
    public static final String TEMPLATE_APPROVED = "MERCHANT_APPROVED";
    public static final String TEMPLATE_REJECTED = "MERCHANT_REJECTED";

    @Override
    public boolean matches(EventEnvelope envelope) {
        return envelope != null && ROUTING_KEY.equalsIgnoreCase(envelope.routingKey());
    }

    @Override
    public List<DispatchNotificationRequest> build(EventEnvelope envelope) {
        String outcome = text(envelope.payload(), "outcome");
        String template = switch (outcome == null ? "" : outcome) {
            case "APPROVED" -> TEMPLATE_APPROVED;
            case "REJECTED" -> TEMPLATE_REJECTED;
            default -> null;
        };
        if (template == null) {
            return List.of();
        }
        Optional<UUID> owner = envelope.extractRecipientId();
        if (owner.isEmpty()) {
            log.warn("Merchant application {} event without an owner; skipping", outcome);
            return List.of();
        }
        Map<String, String> params = new HashMap<>();
        params.put("businessName", Optional.ofNullable(text(envelope.payload(), "businessName")).orElse("your business"));
        params.put("district", districtName(text(envelope.payload(), "operatingDistrict")));
        params.put("reason", Optional.ofNullable(text(envelope.payload(), "reason")).orElse("No reason was given."));
        return NotificationTrigger.email(owner.get(), template, params, null);
    }

    @Override
    public List<TeamNotification> teamNotifications(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        if (!"SUBMITTED".equals(text(payload, "outcome"))) {
            return List.of();
        }
        SriLankanDistrict district = parseDistrict(text(payload, "operatingDistrict"));
        if (district == null) {
            return List.of();
        }
        String business = Optional.ofNullable(text(payload, "businessName")).orElse("A business");
        return List.of(new TeamNotification(InboxAudience.districtOperations(district), TYPE_SUBMITTED,
                "New merchant application",
                business + " applied to ship with SwiftDrop from " + districtName(district.name())
                        + ". Review it in Merchant applications.",
                null));
    }

    private static String text(JsonNode payload, String field) {
        return payload != null && payload.hasNonNull(field) && !payload.get(field).asText().isBlank()
                ? payload.get(field).asText().trim() : null;
    }

    private static SriLankanDistrict parseDistrict(String value) {
        try {
            return value == null ? null : SriLankanDistrict.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** {@code NUWARA_ELIYA} -> {@code Nuwara Eliya}. */
    private static String districtName(String value) {
        if (value == null) {
            return "your district";
        }
        StringBuilder name = new StringBuilder();
        for (String part : value.toLowerCase().split("_")) {
            if (!part.isEmpty()) {
                name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return name.toString();
    }
}
