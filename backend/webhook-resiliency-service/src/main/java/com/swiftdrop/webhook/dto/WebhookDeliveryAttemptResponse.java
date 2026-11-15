package com.swiftdrop.webhook.dto;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * One HTTP attempt as shown to API callers. Deliberately carries no response body from the remote endpoint and
 * no raw exception text: the endpoint's reply must never be readable through this API.
 */
@Schema(description = "Webhook HTTP delivery attempt record. Never contains the remote endpoint's response body.")
public record WebhookDeliveryAttemptResponse(
        @Schema(description = "Attempt UUID")
        UUID attemptId,

        @Schema(description = "Endpoint UUID")
        UUID endpointId,

        @Schema(description = "Merchant UUID")
        UUID merchantId,

        @Schema(description = "Triggering domain event type")
        String eventType,

        @Schema(description = "HTTP status code returned by the endpoint; null when no HTTP response was received "
                + "(timeout or connection failure)")
        Integer httpStatusCode,

        @Schema(description = "Attempt sequential number (1, 2, 3...)")
        int attemptNumber,

        @Schema(description = "Whether delivery succeeded (2xx response)")
        boolean success,

        @Schema(description = "Failure classification; null on success")
        AttemptErrorClass errorClass,

        @Schema(description = "Sanitised failure summary from a fixed set of sentences; null on success")
        String errorMessage,

        @Schema(description = "Round-trip time in milliseconds; null for attempts recorded before it was measured")
        Integer latencyMs,

        @Schema(description = "Attempt execution timestamp")
        Instant attemptedAt
) {
}
