package com.swiftdrop.contracts.events.delivery;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.FeeRevisionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a merchant or dispatcher revises the delivery fee for an order.
 * Routing key: {@code parcel.fee.revised}
 *
 * <p>Fee revisions are private customer/merchant information and must only be
 * delivered to authenticated STOMP customer topics and secured REST clients.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParcelFeeRevisedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        @JsonAlias("traceId")
        UUID correlationId,

        @JsonProperty("trackingNumber")
        String trackingNumber,

        @JsonProperty("orderId")
        UUID orderId,

        @JsonProperty("customerId")
        UUID customerId,

        @JsonProperty("originalFee")
        BigDecimal originalFee,

        @JsonProperty("revisedFee")
        BigDecimal revisedFee,

        @JsonProperty("currency")
        String currency,

        @JsonProperty("reason")
        String reason,

        @JsonProperty("status")
        FeeRevisionStatus status,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public ParcelFeeRevisedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (currency == null) currency = "LKR";
        if (status == null) status = FeeRevisionStatus.PENDING;
        if (timestamp == null) timestamp = Instant.now();
    }
}
