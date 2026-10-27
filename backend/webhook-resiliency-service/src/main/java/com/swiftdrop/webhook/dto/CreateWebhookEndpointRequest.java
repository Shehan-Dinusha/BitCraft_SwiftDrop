package com.swiftdrop.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

@Schema(description = "Webhook subscription creation request")
public record CreateWebhookEndpointRequest(
        @NotNull(message = "Merchant UUID is required")
        @Schema(description = "Owning merchant UUID", example = "22222222-2222-2222-2222-222222222222")
        UUID merchantId,

        @NotBlank(message = "Target URL is required")
        @URL(message = "Target URL must be a valid HTTP/HTTPS endpoint")
        @Schema(description = "Merchant receiving HTTPS endpoint", example = "https://merchant.example.com/api/v1/webhooks")
        String targetUrl,

        @NotBlank(message = "Subscribed events required")
        @Schema(description = "Comma-delimited event patterns (e.g. parcel.status.changed, order.created)", example = "parcel.status.changed,order.created")
        String subscribedEvents,

        @Schema(description = "Endpoint descriptive label", example = "Production ERP Notification Listener")
        String description
) {
}
