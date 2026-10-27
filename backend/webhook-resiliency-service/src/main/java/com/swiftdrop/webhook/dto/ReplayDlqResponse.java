package com.swiftdrop.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Result of DLQ redelivery attempt")
public record ReplayDlqResponse(
        @Schema(description = "DLQ Record UUID")
        UUID dlqId,

        @Schema(description = "Redelivery status: SUCCESS or FAILED")
        String status,

        @Schema(description = "Target HTTP status code")
        Integer statusCode,

        @Schema(description = "Execution message or error")
        String message,

        @Schema(description = "Timestamp of redelivery attempt")
        Instant replayedAt
) {
}
