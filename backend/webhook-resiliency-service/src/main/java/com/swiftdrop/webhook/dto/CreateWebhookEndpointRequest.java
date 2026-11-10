package com.swiftdrop.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

@Schema(description = "Webhook subscription creation request")
public record CreateWebhookEndpointRequest(
        @Schema(description = "Owning merchant UUID. Omit as a merchant: the merchant identity of the caller's token is "
                + "used and any other value is refused. Required for SYSTEM_ADMIN.",
                example = "22222222-2222-2222-2222-222222222222", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID merchantId,

        @NotBlank(message = "Target URL is required")
        @URL(message = "Target URL must be a valid HTTP/HTTPS endpoint")
        @Schema(description = "Merchant receiving HTTPS endpoint", example = "https://merchant.example.com/api/v1/webhooks")
        String targetUrl,

        @NotBlank(message = "Subscribed events required")
        @Schema(description = "Comma-delimited event subscriptions. Each token is an event type (order.created, "
                + "parcel.status.changed), a prefix wildcard of whole segments such as parcel.*, or * for all events. "
                + "Empty, malformed or unknown tokens are rejected with 400.",
                example = "parcel.status.changed,order.created")
        String subscribedEvents,

        @Schema(description = "Endpoint descriptive label", example = "Production ERP Notification Listener")
        String description
) {
}
