package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "The parcels the courier takes from a shop, and the one payment for them.")
public record CollectShopRequest(
        @NotEmpty(message = "Choose at least one parcel")
        @Size(max = 200, message = "Collect at most 200 parcels at once")
        @Schema(description = "Delivery session UUIDs; the shop's other parcels stay for later")
        List<@NotNull UUID> deliveryIds,

        @NotNull(message = "Confirm the cash you collected")
        @DecimalMin(value = "0.00", message = "The amount cannot be negative")
        @Schema(description = "Cash collected; must be exactly the fees of these parcels", example = "4650.00")
        BigDecimal cashCollected
) {
}
