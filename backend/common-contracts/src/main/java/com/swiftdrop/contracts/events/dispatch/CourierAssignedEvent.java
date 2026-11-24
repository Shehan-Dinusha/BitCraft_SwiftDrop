package com.swiftdrop.contracts.events.dispatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a courier is successfully assigned to an order.
 * Routing key: {@code dispatch.courier.assigned}
 *
 * <p>{@code trackingNumber}, {@code merchantId}, {@code customerId}, {@code pickupDistrict} and
 * {@code deliveryDistrict} were added as nullable, additive fields for downstream correlation:
 * delivery-execution-service only learns about an order from this event, and its
 * {@code parcel.status.changed} events need the real tracking number (live tracking), the
 * customer (notifications) and the districts (analytics rejects status events without one).</p>
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

        @JsonProperty("customerId")
        UUID customerId,

        @JsonProperty("pickupDistrict")
        SriLankanDistrict pickupDistrict,

        @JsonProperty("deliveryDistrict")
        SriLankanDistrict deliveryDistrict,

        /** The shop's name, for a merchant order collected with the shop's other parcels; null otherwise. */
        @JsonProperty("merchantName")
        String merchantName,

        /**
         * The order's speed, which decides the route: Same day goes straight to the recipient, Standard and Express
         * through the hub. Null from older publishers, which then follow the district-only route.
         */
        @JsonProperty("speed")
        OrderSpeed speed

) {
    public CourierAssignedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
        if (assignedAt == null) assignedAt = Instant.now();
        // trackingNumber, merchantId, customerId and the districts are intentionally nullable
    }

    /** For callers without the speed. */
    public CourierAssignedEvent(
            UUID eventId,
            UUID correlationId,
            UUID orderId,
            UUID courierId,
            String courierName,
            int estimatedPickupMinutes,
            Instant assignedAt,
            Instant timestamp,
            String trackingNumber,
            UUID merchantId,
            UUID customerId,
            SriLankanDistrict pickupDistrict,
            SriLankanDistrict deliveryDistrict,
            String merchantName
    ) {
        this(eventId, correlationId, orderId, courierId, courierName, estimatedPickupMinutes, assignedAt, timestamp,
                trackingNumber, merchantId, customerId, pickupDistrict, deliveryDistrict, merchantName, null);
    }

    /** For callers without a merchant name. */
    public CourierAssignedEvent(
            UUID eventId,
            UUID correlationId,
            UUID orderId,
            UUID courierId,
            String courierName,
            int estimatedPickupMinutes,
            Instant assignedAt,
            Instant timestamp,
            String trackingNumber,
            UUID merchantId,
            UUID customerId,
            SriLankanDistrict pickupDistrict,
            SriLankanDistrict deliveryDistrict
    ) {
        this(eventId, correlationId, orderId, courierId, courierName, estimatedPickupMinutes, assignedAt, timestamp,
                trackingNumber, merchantId, customerId, pickupDistrict, deliveryDistrict, null);
    }

    /**
     * Backward-compatible constructor for existing callers who do not provide
     * {@code trackingNumber}, {@code merchantId}, and {@code customerId}.
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
        this(eventId, correlationId, orderId, courierId, courierName, estimatedPickupMinutes, assignedAt, timestamp, null, null, null, null, null);
    }

    /**
     * Backward-compatible constructor for callers who provide
     * {@code trackingNumber} and {@code merchantId}, but not {@code customerId}.
     */
    public CourierAssignedEvent(
            UUID eventId,
            UUID correlationId,
            UUID orderId,
            UUID courierId,
            String courierName,
            int estimatedPickupMinutes,
            Instant assignedAt,
            Instant timestamp,
            String trackingNumber,
            UUID merchantId
    ) {
        this(eventId, correlationId, orderId, courierId, courierName, estimatedPickupMinutes, assignedAt, timestamp, trackingNumber, merchantId, null, null, null);
    }

    /**
     * Backward-compatible constructor for callers who provide {@code customerId}
     * but not the districts.
     */
    public CourierAssignedEvent(
            UUID eventId,
            UUID correlationId,
            UUID orderId,
            UUID courierId,
            String courierName,
            int estimatedPickupMinutes,
            Instant assignedAt,
            Instant timestamp,
            String trackingNumber,
            UUID merchantId,
            UUID customerId
    ) {
        this(eventId, correlationId, orderId, courierId, courierName, estimatedPickupMinutes, assignedAt, timestamp, trackingNumber, merchantId, customerId, null, null);
    }
}
