package com.swiftdrop.contracts.events.merchant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.SriLankanDistrict;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by merchant-b2b-service when a business application is submitted (or resubmitted after a
 * rejection), approved or rejected. notification-service alerts the district's dispatcher about a new
 * application and emails the owner the decision.
 * Routing key: {@link com.swiftdrop.contracts.events.EventRoutingKeys#MERCHANT_APPLICATION}
 *
 * <p>The owner's address never travels on the bus: notification-service resolves it from IAM by
 * {@code recipientId}. Approval itself is still announced to IAM by {@code merchant.approved}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MerchantApplicationEvent(

        @JsonProperty("eventId")
        UUID eventId,

        @JsonProperty("correlationId")
        UUID correlationId,

        @JsonProperty("merchantId")
        UUID merchantId,

        /** IAM user id of the business owner who applied. */
        @JsonProperty("recipientId")
        UUID recipientId,

        @JsonProperty("businessName")
        String businessName,

        /** District of the business's pickup location; decides which dispatcher reviews it. */
        @JsonProperty("operatingDistrict")
        SriLankanDistrict operatingDistrict,

        @JsonProperty("outcome")
        Outcome outcome,

        /** Why the application was rejected; null otherwise. */
        @JsonProperty("reason")
        String reason,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public MerchantApplicationEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (timestamp == null) timestamp = Instant.now();
    }

    /** What happened to the application. */
    public enum Outcome {
        SUBMITTED,
        APPROVED,
        REJECTED
    }
}
