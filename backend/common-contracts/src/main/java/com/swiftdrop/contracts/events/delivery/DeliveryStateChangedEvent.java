package com.swiftdrop.contracts.events.delivery;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.DeliveryState;

import java.time.Instant;
import java.util.UUID;

/**
 * Published whenever a delivery transitions between lifecycle states.
 * Routing key: {@code delivery.state.changed}
 */
public record DeliveryStateChangedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("deliveryId")
        UUID deliveryId,

        @JsonProperty("orderId")
        UUID orderId,

        @JsonProperty("courierId")
        UUID courierId,

        @JsonProperty("previousState")
        DeliveryState previousState,

        @JsonProperty("newState")
        DeliveryState newState,

        @JsonProperty("notes")
        String notes,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public DeliveryStateChangedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }
}
