package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "A hub's cash: what couriers hold, what waits to be counted, what is ready to bank.")
public record CashDeskResponse(
        @Schema(description = "District; null for every district") SriLankanDistrict district,
        @Schema(description = "ISO currency", example = "LKR") String currency,
        @Schema(description = "Couriers holding cash") List<CashHoldingResponse> withCouriers,
        @Schema(description = "Handovers waiting to be counted") List<CashHandoverResponse> toCount,
        @Schema(description = "Counted handovers not banked yet") List<CashHandoverResponse> atHub,
        @Schema(description = "Refunds paid from the hub's cash, not yet taken off a deposit") List<CashEntryResponse> refunds,
        @Schema(description = "Cash at the hub: counted minus refunds", example = "25400.00") BigDecimal hubCash,
        @Schema(description = "Latest deposits, newest first") List<CashDepositResponse> deposits
) {
}
