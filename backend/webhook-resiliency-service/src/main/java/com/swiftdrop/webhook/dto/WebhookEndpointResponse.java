package com.swiftdrop.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Endpoint details for GET, list and status responses. Deliberately has no signing-secret field: the secret is
 * returned once, at creation, by {@link WebhookEndpointCreatedResponse}.
 */
@Schema(description = "Webhook subscription endpoint details. Never contains the signing secret.")
public record WebhookEndpointResponse(
        @Schema(description = "Endpoint UUID")
        UUID endpointId,

        @Schema(description = "Owning merchant UUID")
        UUID merchantId,

        @Schema(description = "Target HTTPS endpoint URL")
        String targetUrl,

        @Schema(description = "Subscribed event pattern list")
        String subscribedEvents,

        @Schema(description = "Whether the endpoint is currently active")
        boolean active,

        @Schema(description = "Descriptive label")
        String description,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {
}
