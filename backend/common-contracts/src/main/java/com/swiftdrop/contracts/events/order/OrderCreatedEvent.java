package com.swiftdrop.contracts.events.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a new customer order is successfully placed.
 * Routing key: {@code order.created}
 *
 * <p>The event intentionally carries districts rather than street addresses so
 * that personally identifiable location data never travels over the event bus.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("orderId")
        UUID orderId,

        @JsonProperty("trackingNumber")
        String trackingNumber,

        @JsonProperty("customerId")
        UUID customerId,

        @JsonProperty("merchantId")
        UUID merchantId,

        @JsonProperty("pickupDistrict")
        SriLankanDistrict pickupDistrict,

        @JsonProperty("deliveryDistrict")
        SriLankanDistrict deliveryDistrict,

        @JsonProperty("speed")
        OrderSpeed speed,

        @JsonProperty("codAmount")
        BigDecimal codAmount,

        @JsonProperty("declaredValue")
        BigDecimal declaredValue,

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
        if (currency == null) currency = "LKR";
        if (timestamp == null) timestamp = Instant.now();
    }
}
