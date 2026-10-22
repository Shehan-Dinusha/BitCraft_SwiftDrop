package com.swiftdrop.contracts.events.delivery;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a courier's GPS location is updated during an active delivery.
 * Routing key: {@code delivery.location.updated}
 */
public record LocationUpdatedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("deliveryId")
        UUID deliveryId,

        @JsonProperty("courierId")
        UUID courierId,

        @JsonProperty("latitude")
        double latitude,

        @JsonProperty("longitude")
        double longitude,

        @JsonProperty("bearingDegrees")
        double bearingDegrees,

        @JsonProperty("speedKmh")
        double speedKmh,

        @JsonProperty("accuracy")
        double accuracy,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public LocationUpdatedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }
}
