package com.swiftdrop.pricing.dto;

import com.swiftdrop.pricing.domain.entity.PricingZone;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Pricing zone")
public record ZoneResponse(
        Long id,
        String zoneCode,
        String zoneName,
        String tier,
        BigDecimal baseFee,
        BigDecimal perKgFee,
        BigDecimal expressMultiplier,
        BigDecimal sameDayMultiplier,
        String status,
        Instant updatedAt) {

    public static ZoneResponse from(PricingZone zone) {
        return new ZoneResponse(
                zone.getId(),
                zone.getZoneCode(),
                zone.getZoneName(),
                zone.getTier(),
                zone.getBaseFee(),
                zone.getPerKgFee(),
                zone.getExpressMultiplier(),
                zone.getSameDayMultiplier(),
                zone.getStatus(),
                zone.getUpdatedAt());
    }
}
