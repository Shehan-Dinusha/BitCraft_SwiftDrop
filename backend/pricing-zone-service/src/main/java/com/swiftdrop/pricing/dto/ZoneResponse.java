package com.swiftdrop.pricing.dto;

import com.swiftdrop.pricing.domain.entity.PricingZone;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Pricing zone")
public record ZoneResponse(
        @Schema(description = "Database identifier", example = "1")
        Long id,
        @Schema(description = "Unique zone code", example = "ZONE_WESTERN")
        String zoneCode,
        @Schema(description = "Display name of the zone", example = "Western Province Metro")
        String zoneName,
        @Schema(description = "Zone tier", example = "METRO")
        String tier,
        @Schema(description = "Base fee in LKR for deliveries inside one district of this zone", example = "350.00")
        BigDecimal baseFee,
        @Schema(description = "Fee in LKR for each extra weight step", example = "80.00")
        BigDecimal perKgFee,
        @Schema(description = "Multiplier applied for EXPRESS delivery", example = "1.50")
        BigDecimal expressMultiplier,
        @Schema(description = "Multiplier applied for SAME_DAY delivery", example = "2.00")
        BigDecimal sameDayMultiplier,
        @Schema(description = "Zone status", example = "ACTIVE")
        String status,
        @Schema(description = "Time of the last update (UTC)", example = "2026-11-14T09:00:00Z")
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