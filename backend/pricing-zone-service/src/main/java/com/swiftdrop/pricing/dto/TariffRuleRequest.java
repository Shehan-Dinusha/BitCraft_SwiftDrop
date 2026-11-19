package com.swiftdrop.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Publish a new version of the default tariff rule")
public record TariffRuleRequest(
        @Schema(description = "Optional. Generated when blank", example = "PRICING-2026-V2")
        @Size(max = 64) String ruleVersionId,
        @Schema(description = "Base price in LKR for different-district deliveries. Covers the first weight step", example = "550.00")
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal basePrice,
        @Schema(description = "Size of one weight step in kg", example = "1.00")
        @NotNull @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal weightStepKg,
        @Schema(description = "Price in LKR for each extra weight step", example = "120.00")
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal pricePerStep,
        @Schema(description = "Divisor used to turn length x width x height (cm) into volumetric weight (kg)", example = "5000.00")
        @NotNull @DecimalMin("1.00") @Digits(integer = 6, fraction = 2) BigDecimal volumetricDivisor) {
}