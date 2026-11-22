package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Revenue and cash for a period, read from the cash ledger. Dates are Asia/Colombo days.")
public record CashReportResponse(
        @Schema(description = "First included date", example = "2026-11-16") LocalDate from,
        @Schema(description = "Last included date", example = "2026-11-22") LocalDate to,
        @Schema(description = "ISO currency", example = "LKR") String currency,
        @Schema(description = "Delivery fees collected minus refunds, under the pickup district", example = "48200.00")
        BigDecimal revenue,
        @Schema(description = "Refunds taken off the revenue", example = "350.00") BigDecimal refunds,
        @Schema(description = "Parcels a fee was collected for", example = "92") long parcels,
        @Schema(description = "Cash on delivery collected; the merchants' money, never revenue", example = "112500.00")
        BigDecimal cod,
        @Schema(description = "Missing from handovers counted in the period", example = "70.00") BigDecimal shortage,
        @Schema(description = "Too much in handovers counted in the period", example = "0.00") BigDecimal excess,
        @Schema(description = "Now: with couriers, not handed over", example = "3170.00") BigDecimal withCouriers,
        @Schema(description = "Now: handed over but not counted", example = "770.00") BigDecimal toCount,
        @Schema(description = "Now: counted at the hub, not banked", example = "1820.00") BigDecimal atHub,
        @Schema(description = "Now: banked, waiting for an admin", example = "25400.00") BigDecimal toConfirm,
        @Schema(description = "One entry per date, including dates without revenue") List<Day> days,
        @Schema(description = "Districts with any revenue or last-mile delivery, highest revenue first")
        List<District> districts
) {

    @Schema(name = "CashReportDay", description = "Revenue of one date")
    public record Day(
            @Schema(description = "Date", example = "2026-11-22") LocalDate date,
            @Schema(description = "Fees minus refunds", example = "6720.00") BigDecimal revenue,
            @Schema(description = "Parcels a fee was collected for", example = "13") long parcels
    ) {
    }

    @Schema(name = "CashReportDistrict", description = "A district's revenue beside the last-mile work it did for others")
    public record District(
            @Schema(description = "District") SriLankanDistrict district,
            @Schema(description = "Fees minus refunds of parcels picked up here", example = "21400.00") BigDecimal revenue,
            @Schema(description = "Parcels a fee was collected for", example = "41") long parcels,
            @Schema(description = "Parcels from other districts delivered here", example = "17") long lastMileDelivered
    ) {
    }
}
