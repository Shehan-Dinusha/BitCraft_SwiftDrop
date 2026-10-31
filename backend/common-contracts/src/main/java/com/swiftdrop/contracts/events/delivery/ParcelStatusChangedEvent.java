package com.swiftdrop.contracts.events.delivery;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;

import java.time.Instant;
import java.util.UUID;

/**
 * Published whenever the delivery FSM advances a parcel.
 * Routing key: {@code parcel.status.changed}
 *
 * <p>The event carries both the public status and the internal queue status so
 * the API Gateway can broadcast a PII-free view without querying another
 * service. Customer and merchant identifiers are optional and are used only
 * for private STOMP topics.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParcelStatusChangedEvent(

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

        @JsonProperty("previousPublicStatus")
        PublicOrderStatus previousPublicStatus,

        @JsonProperty("newPublicStatus")
        @JsonAlias("newStatus")
        PublicOrderStatus newPublicStatus,

        @JsonProperty("internalSubStatus")
        InternalQueueStatus internalSubStatus,

        @JsonProperty("district")
        SriLankanDistrict district,

        @JsonProperty("destinationDistrict")
        SriLankanDistrict destinationDistrict,

        @JsonProperty("failureReason")
        DeliveryFailureReason failureReason,

        @JsonProperty("timestamp")
        Instant timestamp

) {
    public ParcelStatusChangedEvent {
        if (eventId == null) eventId = UUID.randomUUID();
        if (correlationId == null) correlationId = UUID.randomUUID();
        if (newPublicStatus == null && internalSubStatus != null) {
            newPublicStatus = internalSubStatus.toPublicStatus();
        }
        if (timestamp == null) timestamp = Instant.now();
    }
}
