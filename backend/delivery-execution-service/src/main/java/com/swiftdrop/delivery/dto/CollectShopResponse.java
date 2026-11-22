package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What the courier collected from a shop.")
public record CollectShopResponse(
        @Schema(description = "Parcels picked up", example = "11") int parcels,
        @Schema(description = "The shop's payment; null when no fee was due") MerchantPaymentResponse payment
) {
}
