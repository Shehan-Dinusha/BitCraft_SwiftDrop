package com.swiftdrop.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "The signed-in courier's cash.")
public record CourierCashResponse(
        @Schema(description = "Cash to hand over", example = "3170.00") BigDecimal inHand,
        @Schema(description = "ISO currency", example = "LKR") String currency,
        @Schema(description = "The parcels it came from, newest first") List<CashEntryResponse> entries,
        @Schema(description = "Latest handovers, newest first") List<CashHandoverResponse> handovers
) {
}
