package com.swiftdrop.contracts.events.order;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an order is cancelled by the customer or system.
 * Routing key: {@code order.cancelled}
 */
public record OrderCancelledEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("orderId")
        UUID orderId,

        @JsonProperty("customerId")
        UUID customerId,

        @JsonProperty("reason")
        String reason,

        @JsonProperty("cancelledBy")
        String cancelledBy,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public OrderCancelledEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }
}
