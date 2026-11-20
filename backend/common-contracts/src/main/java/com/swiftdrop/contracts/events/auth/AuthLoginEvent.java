package com.swiftdrop.contracts.events.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by auth-iam-service after a successful sign-in, or after a wrong password for an
 * existing account. notification-service emails a security alert to the account's registered address.
 * Routing key: {@link com.swiftdrop.contracts.events.EventRoutingKeys#AUTH_LOGIN}
 *
 * <p>The address never travels on the bus: notification-service resolves it from IAM by
 * {@code recipientId}. {@code ipAddress} and {@code userAgent} are shown (the IP partly masked)
 * so the user can recognise the sign-in.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthLoginEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        /** IAM public id of the account. */
        @JsonProperty("recipientId")
        UUID recipientId,

        @JsonProperty("outcome")
        LoginOutcome outcome,

        @JsonProperty("ipAddress")
        String ipAddress,

        @JsonProperty("userAgent")
        String userAgent,

        @JsonProperty("occurredAt")
        Instant occurredAt,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public AuthLoginEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
        if (timestamp == null) timestamp = Instant.now();
    }

    /** Result of the sign-in attempt. */
    public enum LoginOutcome {
        SUCCESS,
        FAILED
    }
}
