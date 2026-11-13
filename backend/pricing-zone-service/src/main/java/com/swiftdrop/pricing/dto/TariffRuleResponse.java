package com.swiftdrop.pricing.dto;

import com.swiftdrop.pricing.domain.entity.TariffRule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Tariff rule version")
public record TariffRuleResponse(
        Long id,
        String ruleVersionId,
        String fromDistrict,
        String toDistrict,
        BigDecimal basePrice,
        BigDecimal weightStepKg,
        BigDecimal pricePerStep,
        BigDecimal volumetricDivisor,
        boolean active,
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
