package com.swiftdrop.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Dead-Letter Queue webhook record")
public record DeadLetterWebhookResponse(
        @Schema(description = "DLQ Record UUID")
        UUID dlqId,

        @Schema(description = "Target Endpoint UUID")
        UUID endpointId,

        @Schema(description = "Merchant UUID")
        UUID merchantId,

        @Schema(description = "Triggering domain event type")
        String eventType,

        @Schema(description = "Payload content")
        String payload,

        @Schema(description = "Root cause failure reason")
        String failureReason,

        @Schema(description = "Total failed delivery attempts before dead-lettering")
        int totalAttempts,

        @Schema(description = "Whether this record has been replayed")
        boolean replayed,

        @Schema(description = "Timestamp of manual replay execution")
        Instant replayedAt,

        @Schema(description = "Dead-lettering timestamp")
        Instant createdAt
) {
}
