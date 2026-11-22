package com.swiftdrop.contracts.events.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a booked order is edited before a courier is assigned. Carries what dispatch needs to
 * plan the order again: districts, speed, size and weight. No addresses or contacts.
 * Routing key: {@code order.updated}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderUpdatedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("orderId")
        UUID orderId,

        @JsonProperty("trackingNumber")
        String trackingNumber,

        @JsonProperty("pickupDistrict")
        SriLankanDistrict pickupDistrict,

        @JsonProperty("deliveryDistrict")
        SriLankanDistrict deliveryDistrict,

        @JsonProperty("speed")
        OrderSpeed speed,

        @JsonProperty("lengthCm")
        BigDecimal lengthCm,

        @JsonProperty("widthCm")
        BigDecimal widthCm,

        @JsonProperty("heightCm")
        BigDecimal heightCm,

        @JsonProperty("actualWeightKg")
        BigDecimal actualWeightKg,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public OrderUpdatedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }
}
