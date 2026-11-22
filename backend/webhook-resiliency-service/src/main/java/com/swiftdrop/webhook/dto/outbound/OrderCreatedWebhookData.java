package com.swiftdrop.webhook.dto.outbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.swiftdrop.contracts.enums.OrderSpeed;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * {@code data} of an {@code order.created} webhook (payload v1).
 *
 * <p>D6 is PROVISIONAL, pending Member 7: no monetary field ({@code declaredValue},
 * {@code estimatedPrice}, {@code currency}) and no pricing or payout data is included. Approved fields can be added
 * later as new, additive properties. Customer id, districts and correlation id are also excluded.</p>
 */
@JsonPropertyOrder({"orderId", "merchantOrderReference", "trackingNumber", "speed"})
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "order.created webhook data (v1). Monetary fields are deliberately absent (D6 provisional).")
public record OrderCreatedWebhookData(
        @Schema(description = "SwiftDrop order id")
        UUID orderId,

        @Schema(description = "The merchant's own order reference. Always null in v1 until the order event carries it.",
                nullable = true)
        String merchantOrderReference,

        @Schema(description = "Tracking number assigned when the order was created", nullable = true)
        String trackingNumber,

        @Schema(description = "Requested delivery speed", nullable = true)
        OrderSpeed speed
) {
}
