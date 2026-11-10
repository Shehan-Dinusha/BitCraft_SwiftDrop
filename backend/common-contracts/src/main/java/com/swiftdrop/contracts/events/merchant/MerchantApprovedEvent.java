package com.swiftdrop.contracts.events.merchant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by merchant-b2b-service when a merchant account is approved.
 * Routing key: {@code merchant.approved}
 *
 * <p>Consumed by auth-iam-service (to provision IAM roles) and
 * notification-service (to send a welcome notification).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MerchantApprovedEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("merchantId")
        UUID merchantId,

        @JsonProperty("ownerUserId")
        UUID ownerUserId,

        @JsonProperty("approvedAt")
        Instant approvedAt,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public MerchantApprovedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (approvedAt == null) approvedAt = Instant.now();
        if (timestamp == null) timestamp = Instant.now();
    }
}

