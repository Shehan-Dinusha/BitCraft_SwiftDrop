package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "A shop the courier visits: its parcels, grouped by the address they are collected from.")
public record ShopStopResponse(
        @Schema(description = "Shop UUID") UUID merchantId,
        @Schema(description = "Shop name", example = "Nimal Fashion") String merchantName,
        @Schema(description = "ISO currency", example = "LKR") String currency,
        @Schema(description = "Usually one address") List<Address> addresses
) {
    @Schema(name = "ShopStopAddress", description = "One pickup address of the shop")
    public record Address(
            @Schema(description = "Contact and address") CourierJobStop pickup,
            @Schema(description = "Parcels collected there, longest waiting first") List<ShopParcel> parcels,
            @Schema(description = "Fees of all of them", example = "5200.00") BigDecimal total
    ) {
    }
}
