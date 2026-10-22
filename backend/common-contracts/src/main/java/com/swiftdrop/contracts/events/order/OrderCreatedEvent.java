package com.swiftdrop.contracts.events.order;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a new customer order is successfully placed.
 * Routing key: {@code order.created}
 */
public record OrderCreatedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("orderId")
        UUID orderId,

        @JsonProperty("customerId")
        UUID customerId,

        @JsonProperty("merchantId")
        UUID merchantId,

        @JsonProperty("pickupAddress")
        String pickupAddress,

        @JsonProperty("deliveryAddress")
        String deliveryAddress,

        @JsonProperty("estimatedPrice")
        BigDecimal estimatedPrice,

        @JsonProperty("currency")
        String currency,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public OrderCreatedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }
}
