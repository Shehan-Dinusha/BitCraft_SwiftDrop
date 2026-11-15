package com.swiftdrop.webhook.dto.outbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * {@code data} of a {@code parcel.status.changed} webhook (payload v1). Public status information only: no customer
 * id, internal sub-status, districts or correlation id.
 */
@JsonPropertyOrder({"orderId", "merchantOrderReference", "trackingNumber", "status", "previousStatus", "failureReason"})
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "parcel.status.changed webhook data (v1)")
public record ParcelStatusChangedWebhookData(
        @Schema(description = "SwiftDrop order id")
        UUID orderId,

        @Schema(description = "The merchant's own order reference. Always null in v1 until the order event carries it.",
                nullable = true)
        String merchantOrderReference,

        @Schema(description = "Tracking number assigned when the order was created", nullable = true)
        String trackingNumber,

        @Schema(description = "New public status")
        PublicOrderStatus status,

        @Schema(description = "Previous public status, if known", nullable = true)
        PublicOrderStatus previousStatus,

        @Schema(description = "Reason for a failed delivery attempt, if any", nullable = true)
        DeliveryFailureReason failureReason
) {
}
