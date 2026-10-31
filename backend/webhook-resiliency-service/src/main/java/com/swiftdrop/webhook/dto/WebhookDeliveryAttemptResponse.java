package com.swiftdrop.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Webhook HTTP delivery attempt record")
public record WebhookDeliveryAttemptResponse(
        @Schema(description = "Attempt UUID")
        UUID attemptId,

        @Schema(description = "Endpoint UUID")
        UUID endpointId,

        @Schema(description = "Merchant UUID")
        UUID merchantId,

        @Schema(description = "Triggering domain event type")
        String eventType,

        @Schema(description = "HTTP response status code returned by merchant endpoint")
        Integer httpStatusCode,

        @Schema(description = "Attempt sequential number (1, 2, 3...)")
        int attemptNumber,

        @Schema(description = "Whether delivery succeeded (2xx response)")
        boolean success,

        @Schema(description = "Sample snippet of response body")
        String responseBody,

        @Schema(description = "Error diagnostic message if attempt failed")
        String errorMessage,

        @Schema(description = "Attempt execution timestamp")
        Instant attemptedAt
) {
}
