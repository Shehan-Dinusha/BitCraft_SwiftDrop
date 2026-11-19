package com.swiftdrop.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned only by endpoint creation. This is the single response that carries the signing secret; every other
 * endpoint response ({@link WebhookEndpointResponse}) omits it, so the merchant must store it now.
 */
@Schema(description = "Newly registered webhook endpoint plus its signing secret. The secret is returned only here, "
        + "once; it cannot be retrieved again through GET or list.")
public record WebhookEndpointCreatedResponse(
        @Schema(description = "The registered endpoint")
        WebhookEndpointResponse endpoint,

        @Schema(description = "HMAC-SHA256 signing secret used to verify X-SwiftDrop-Signature. Shown once; store it "
                + "securely.", example = "whsec_3f9c…")
        String signingSecret
) {
}
