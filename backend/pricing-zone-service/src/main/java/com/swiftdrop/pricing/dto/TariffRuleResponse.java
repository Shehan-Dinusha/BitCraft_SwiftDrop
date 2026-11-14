package com.swiftdrop.pricing.dto;

import com.swiftdrop.pricing.domain.entity.TariffRule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Tariff rule version")
public record TariffRuleResponse(
        @Schema(description = "Database identifier", example = "1")
        Long id,
        @Schema(description = "Version identifier of the rule", example = "PRICING-2026-V1")
        String ruleVersionId,
        @Schema(description = "Origin district, or ALL for the default rule", example = "ALL")
        String fromDistrict,
        @Schema(description = "Destination district, or ALL for the default rule", example = "ALL")
        String toDistrict,
        @Schema(description = "Base price in LKR. Covers the first weight step", example = "550.00")
        BigDecimal basePrice,
        @Schema(description = "Size of one weight step in kg", example = "1.00")
        BigDecimal weightStepKg,
        @Schema(description = "Price in LKR for each extra weight step", example = "120.00")
        BigDecimal pricePerStep,
        @Schema(description = "Divisor used to compute volumetric weight", example = "5000.00")
        BigDecimal volumetricDivisor,
        @Schema(description = "Whether this version is the active one", example = "true")
        boolean active,
        @Schema(description = "Time the version was created (UTC)", example = "2026-11-14T09:00:00Z")
        Instant createdAt) {

    public static TariffRuleResponse from(TariffRule rule) {
        return new TariffRuleResponse(
                rule.getId(),
                rule.getRuleVersionId(),
                rule.getFromDistrict(),
                rule.getToDistrict(),
                rule.getBasePrice(),
                rule.getWeightStepKg(),
                rule.getPricePerStep(),
                rule.getVolumetricDivisor(),
                Boolean.TRUE.equals(rule.getIsActive()),
                rule.getCreatedAt());
    }
}