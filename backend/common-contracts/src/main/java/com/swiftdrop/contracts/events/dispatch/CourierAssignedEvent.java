package com.swiftdrop.contracts.events.dispatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.SriLankanDistrict;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a courier is successfully assigned to an order.
 * Routing key: {@code dispatch.courier.assigned}
 *
 * <p>{@code trackingNumber} and {@code merchantId} were added as nullable,
 * additive fields requested by Members 5 and 6 for downstream correlation
 * in delivery-execution-service and notification-service.</p>
 * <p>Optional pickup/delivery districts let delivery preserve route context in
 * public status events. Older producers remain compatible; new publishers supply both.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourierAssignedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("orderId")
        UUID orderId,

        @JsonProperty("courierId")
        UUID courierId,

        @JsonProperty("courierName")
        String courierName,

        @JsonProperty("estimatedPickupMinutes")
        int estimatedPickupMinutes,

        @JsonProperty("assignedAt")
        Instant assignedAt,

        @JsonProperty("timestamp")
        Instant timestamp,

        @JsonProperty("trackingNumber")
        String trackingNumber,

        @JsonProperty("merchantId")
        UUID merchantId,

        @JsonProperty("pickupDistrict")
        SriLankanDistrict pickupDistrict,

        @JsonProperty("deliveryDistrict")
        SriLankanDistrict deliveryDistrict

) {
    public CourierAssignedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
        if (assignedAt == null) assignedAt = Instant.now();
        // trackingNumber and merchantId are intentionally nullable
    }

    /** Legacy assignments did not include districts; new publishers provide both. */
    public CourierAssignedEvent(UUID eventId, UUID correlationId, UUID orderId, UUID courierId,
            String courierName, int estimatedPickupMinutes, Instant assignedAt, Instant timestamp,
            String trackingNumber, UUID merchantId) {
        this(eventId, correlationId, orderId, courierId, courierName, estimatedPickupMinutes,
                assignedAt, timestamp, trackingNumber, merchantId, null, null);
    }

    /**
     * Backward-compatible constructor for existing callers who do not provide
     * {@code trackingNumber} and {@code merchantId}.
     */
    public CourierAssignedEvent(
            UUID eventId,
            UUID correlationId,
            UUID orderId,
            UUID courierId,
            String courierName,
            int estimatedPickupMinutes,
            Instant assignedAt,
            Instant timestamp
    ) {
        this(eventId, correlationId, orderId, courierId, courierName, estimatedPickupMinutes, assignedAt, timestamp, null, null);
    }
}
