package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Cash a courier has not handed over, for service-to-service use.")
public record InternalCourierCashResponse(
        @Schema(description = "Amount", example = "3170.00") BigDecimal inHand,
        @Schema(description = "ISO currency", example = "LKR") String currency
) {
}
