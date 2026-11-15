package com.swiftdrop.contracts.events.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by auth-iam-service when a one-time code must be emailed to a user.
 * Routing key: {@link com.swiftdrop.contracts.events.EventRoutingKeys#AUTH_OTP_REQUESTED}
 *
 * <p>Consumed by notification-service, which resolves the user's email address from IAM by
 * {@code recipientId} and sends the code. The address itself never travels on the bus.
 *
 * <p><strong>Sensitive:</strong> {@code code} is a short-lived secret (minutes). Consumers must
 * not persist it in logs, dispatch audit rows or in-app inboxes, and must not log this payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthOtpRequestedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        /** IAM public id of the user the code is for (named so notification-service can resolve it). */
        @JsonProperty("recipientId")
        UUID recipientId,

        @JsonProperty("purpose")
        OtpPurpose purpose,

        @JsonProperty("code")
        String code,

        @JsonProperty("expiresInMinutes")
        int expiresInMinutes,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public AuthOtpRequestedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }

    /** Never print the code, even by accident in a log statement. */
    @Override
    public String toString() {
        return "AuthOtpRequestedEvent[eventId=" + eventId + ", recipientId=" + recipientId
                + ", purpose=" + purpose + ", code=***]";
    }
}
