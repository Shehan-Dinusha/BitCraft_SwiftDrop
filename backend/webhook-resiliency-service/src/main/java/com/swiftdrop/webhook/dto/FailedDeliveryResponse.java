package com.swiftdrop.webhook.dto;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.domain.entity.WebhookDeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** A webhook delivery that used up its attempts. Never contains the payload, the URL or the endpoint's reply. */
@Schema(description = "A webhook delivery that failed for good, with its latest replay if there is one.")
public record FailedDeliveryResponse(
        @Schema(description = "Delivery to replay")
        UUID deliveryId,

        @Schema(description = "Endpoint it was sent to")
        UUID endpointId,

        @Schema(description = "Event id, the same in every attempt and replay")
        UUID eventId,

        @Schema(description = "Event type", example = "parcel.status.changed")
        String eventType,

        @Schema(description = "Order the event is about", nullable = true)
        UUID orderId,

        @Schema(description = "Tracking number of that order", nullable = true)
        String trackingNumber,

        @Schema(description = "Attempts made")
        int attempts,

        @Schema(description = "HTTP status of the last attempt; null when there was no response", nullable = true)
        Integer lastHttpStatus,

        @Schema(description = "Why the last attempt failed", nullable = true)
        AttemptErrorClass errorClass,

        @Schema(description = "The same reason as a sentence, from a fixed set")
        String errorMessage,

        @Schema(description = "When the event happened")
        Instant occurredAt,

        @Schema(description = "When it was given up on")
        Instant failedAt,

        @Schema(description = "Status of the latest replay; null when it was never replayed", nullable = true)
        WebhookDeliveryStatus replayStatus,

        @Schema(description = "When the latest replay was asked for", nullable = true)
        Instant replayedAt
) {
}
