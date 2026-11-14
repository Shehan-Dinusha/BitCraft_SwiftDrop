package com.swiftdrop.webhook.dto.outbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Public, versioned envelope of every outbound merchant webhook (payload v1, decision D6). This is the HTTP body
 * merchants receive, not an API response of this service. Field order and null handling are fixed so the same
 * source event always serialises to the same bytes.
 *
 * @param data {@link OrderCreatedWebhookData} or {@link ParcelStatusChangedWebhookData}, chosen by {@code type}
 */
@JsonPropertyOrder({"id", "type", "apiVersion", "occurredAt", "data"})
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "SwiftDrop merchant webhook payload, version 1")
public record WebhookPayloadV1(
        @Schema(description = "Event id. Stable across retries and replays; use it to de-duplicate.")
        UUID id,

        @Schema(description = "Event type", allowableValues = {"order.created", "parcel.status.changed"})
        String type,

        @Schema(description = "Payload version", example = "1")
        int apiVersion,

        @Schema(description = "When the event happened at its source (UTC, microsecond precision), not when it was sent")
        Instant occurredAt,

        @Schema(description = "Event-specific data")
        Object data
) {
    public static final int API_VERSION = 1;
}
