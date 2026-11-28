package com.swiftdrop.webhook.dto;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Legacy dead-letter record (read-only history), shown to its merchant or a system administrator.
 */
@Schema(description = "Legacy dead-letter webhook record (read-only history)")
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

        @Schema(description = "Raw failure detail recorded by the retired dispatcher; may contain the callback URL or host")
        String failureReason,

        @Schema(description = "Failure classification derived from the recorded failure")
        AttemptErrorClass errorClass,

        @Schema(description = "Sanitised failure summary; never contains URLs, hosts or raw error text",
                example = "Endpoint returned a server error (HTTP 503)")
        String failureSummary,

        @Schema(description = "Total failed delivery attempts before dead-lettering")
        int totalAttempts,

        @Schema(description = "Whether this record has been replayed")
        boolean replayed,

        @Schema(description = "Timestamp of manual replay execution")
        Instant replayedAt,

        @Schema(description = "Dead-lettering timestamp (the time of the final failed attempt)")
        Instant createdAt
) {
}
