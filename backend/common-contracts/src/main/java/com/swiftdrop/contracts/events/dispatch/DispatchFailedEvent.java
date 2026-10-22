package com.swiftdrop.contracts.events.dispatch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when dispatch assignment fails (no available couriers, zone mismatch, etc.).
 * Routing key: {@code dispatch.failed}
 */
public record DispatchFailedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("orderId")
        UUID orderId,

        @JsonProperty("failureReason")
        String failureReason,

        @JsonProperty("attemptCount")
        int attemptCount,

        @JsonProperty("retryable")
        boolean retryable,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public DispatchFailedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }
}
