package com.swiftdrop.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A parcel still at the shop, for the merchant service.")
public record InternalMerchantParcel(
        @Schema(description = "Order UUID") UUID orderId,
        @Schema(description = "Tracking number") String trackingNumber,
        @Schema(description = "WAITING, READY or ASSIGNED") MerchantPickupState state,
        @Schema(description = "Courier coming for it") String courierName,
        @Schema(description = "When the shop said it is packed") Instant readyAt
) {
}
