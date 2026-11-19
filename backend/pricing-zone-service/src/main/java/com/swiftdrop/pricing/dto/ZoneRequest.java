package com.swiftdrop.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Create or update a pricing zone")
public record ZoneRequest(
        @Schema(description = "Unique zone code. Cannot be changed on update", example = "ZONE_WESTERN")
        @NotBlank @Size(max = 32) String zoneCode,
        @Schema(description = "Display name of the zone", example = "Western Province")
        @NotBlank @Size(max = 128) String zoneName,
        @Schema(description = "Zone tier", example = "METRO")
        @NotBlank @Size(max = 32) String tier,
        @Schema(description = "Base fee in LKR for deliveries inside one district of this zone", example = "350.00")
        @NotNull @DecimalMin("0.00") BigDecimal baseFee,
        @Schema(description = "Fee in LKR for each extra weight step", example = "80.00")
        @NotNull @DecimalMin("0.00") BigDecimal perKgFee,
        @Schema(description = "Multiplier applied for EXPRESS delivery (at least 1.00)", example = "1.50")
        @NotNull @DecimalMin("1.00") BigDecimal expressMultiplier,
        @Schema(description = "Multiplier applied for SAME_DAY delivery (at least 1.00)", example = "2.00")
        @NotNull @DecimalMin("1.00") BigDecimal sameDayMultiplier,
        @Schema(description = "Optional. Defaults to ACTIVE on create and keeps the current value on update", example = "ACTIVE")
        @Size(max = 64) String status) {
}
