package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "The shop's parcels that no courier has collected yet, and the cash to prepare for them.")
public record MerchantPickupResponse(
        @Schema(description = "Not marked ready yet") Group waiting,
        @Schema(description = "Ready, no courier yet") Group ready,
        @Schema(description = "A courier is coming") Group assigned,
        @Schema(description = "ISO currency", example = "LKR") String currency,
        @Schema(description = "The parcels, longest waiting first") List<Parcel> parcels
) {
    @Schema(name = "MerchantPickupGroup", description = "How many parcels, and their fees")
    public record Group(
            @Schema(description = "Parcels", example = "12") int parcels,
            @Schema(description = "Fees to pay in cash at pickup", example = "5200.00") BigDecimal total,
            @Schema(description = "Parcels from before fees were kept, so not in the total", example = "0") int withoutFee
    ) {
    }

    @Schema(name = "MerchantPickupParcel", description = "A parcel at the shop")
    public record Parcel(
            @Schema(description = "Order UUID") UUID orderId,
            @Schema(description = "Tracking number", example = "SD-7K4M-9QX2") String trackingNumber,
            @Schema(description = "The shop's own order number", example = "SHOP-1001") String externalRef,
            @Schema(description = "WAITING, READY or ASSIGNED") MerchantPickupState state,
            @Schema(description = "Courier coming for it") String courierName,
            @Schema(description = "When it was marked ready") Instant readyAt,
            @Schema(description = "Delivery fee; null for older orders", example = "350.00") BigDecimal fee
    ) {
    }
}
