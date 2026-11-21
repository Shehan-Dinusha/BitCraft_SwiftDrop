package com.swiftdrop.contracts.events.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by auth-iam-service when a staff account (courier or dispatcher) is created or its invite
 * is re-sent. notification-service emails the invite link, where the person chooses their own password.
 * Routing key: {@link com.swiftdrop.contracts.events.EventRoutingKeys#AUTH_STAFF_INVITE}
 *
 * <p>The address never travels on the bus: notification-service resolves it from IAM by
 * {@code recipientId}.
 *
 * <p><strong>Sensitive:</strong> {@code inviteUrl} contains a single-use secret that signs the person
 * in. Consumers must not persist it in logs, dispatch audit rows or in-app inboxes, and must not log
 * this payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthStaffInviteEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        /** IAM public id of the invited account. */
        @JsonProperty("recipientId")
        UUID recipientId,

        @JsonProperty("role")
        UserRole role,

        /** Frontend link that carries the invite token in its fragment. */
        @JsonProperty("inviteUrl")
        String inviteUrl,

        @JsonProperty("expiresInHours")
        int expiresInHours,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public AuthStaffInviteEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }

    /** Never print the link, even by accident in a log statement. */
    @Override
    public String toString() {
        return "AuthStaffInviteEvent[eventId=" + eventId + ", recipientId=" + recipientId
                + ", role=" + role + ", inviteUrl=***]";
    }
}
