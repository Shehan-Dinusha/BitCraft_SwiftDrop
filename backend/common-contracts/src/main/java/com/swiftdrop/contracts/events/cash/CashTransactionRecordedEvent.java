package com.swiftdrop.contracts.events.cash;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.CashTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when cash-on-delivery money is collected, remitted, or refunded.
 * Routing key: {@code cash.transaction.recorded}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CashTransactionRecordedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        @JsonAlias("traceId")
        UUID correlationId,

        @JsonProperty("transactionId")
        UUID transactionId,

        @JsonProperty("orderId")
        UUID orderId,

        @JsonProperty("trackingNumber")
        String trackingNumber,

        @JsonProperty("merchantId")
        UUID merchantId,

        @JsonProperty("courierId")
        UUID courierId,

        @JsonProperty("type")
        CashTransactionType type,

        @JsonProperty("amount")
        BigDecimal amount,

        @JsonProperty("currency")
        String currency,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public CashTransactionRecordedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (currency == null) currency = "LKR";
        if (timestamp == null) timestamp = Instant.now();
    }
}
