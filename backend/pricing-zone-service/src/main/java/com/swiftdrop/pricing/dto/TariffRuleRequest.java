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
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal basePrice,
        @NotNull @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal weightStepKg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal pricePerStep,
        @NotNull @DecimalMin("1.00") @Digits(integer = 6, fraction = 2) BigDecimal volumetricDivisor) {
}
