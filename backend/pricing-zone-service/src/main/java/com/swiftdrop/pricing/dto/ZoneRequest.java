package com.swiftdrop.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Create or update a pricing zone")
public record ZoneRequest(
        @Schema(example = "ZONE_WESTERN") @NotBlank @Size(max = 32) String zoneCode,
        @Schema(example = "Western Province") @NotBlank @Size(max = 128) String zoneName,
        @Schema(example = "METRO") @NotBlank @Size(max = 32) String tier,
        @NotNull @DecimalMin("0.00") BigDecimal baseFee,
        @NotNull @DecimalMin("0.00") BigDecimal perKgFee,
        @NotNull @DecimalMin("1.00") BigDecimal expressMultiplier,
        @NotNull @DecimalMin("1.00") BigDecimal sameDayMultiplier,
        @Size(max = 64) String status) {
}
