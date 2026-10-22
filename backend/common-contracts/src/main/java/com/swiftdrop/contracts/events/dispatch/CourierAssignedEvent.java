package com.swiftdrop.contracts.events.dispatch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a courier is successfully assigned to an order.
 * Routing key: {@code dispatch.courier.assigned}
 */
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
        Instant timestamp

) {
    public CourierAssignedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
        if (assignedAt == null) assignedAt = Instant.now();
    }
}
