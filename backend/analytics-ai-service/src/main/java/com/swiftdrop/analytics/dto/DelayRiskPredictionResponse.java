package com.swiftdrop.analytics.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "AI-calculated delay risk assessment and mitigation advice")
public record DelayRiskPredictionResponse(
        @Schema(description = "Insight record UUID", example = "d1000000-0000-0000-0000-000000000001")
        UUID insightId,

        @Schema(description = "Origin district", example = "COLOMBO")
        SriLankanDistrict originDistrict,

        @Schema(description = "Destination district", example = "KANDY")
        SriLankanDistrict destinationDistrict,

        @Schema(description = "Delivery service level", example = "STANDARD")
        String deliverySpeed,

        @Schema(description = "Categorical risk level (LOW, MEDIUM, HIGH, CRITICAL)", example = "MEDIUM")
        String riskLevel,

        @Schema(description = "Risk probability score (0.0 to 100.0)", example = "38.50")
        BigDecimal riskScore,

        @Schema(description = "Predicted delay in minutes beyond baseline ETA", example = "25")
        int predictedDelayMinutes,

        @Schema(description = "Recommended operational mitigation", example = "DISPATCH_EARLY_INTERDISTRICT_HUB")
        String recommendedAction,

        @Schema(description = "AI reasoning and contributing factors", example = "Rainy weather along Kadugannawa pass combined with moderate peak traffic adds 25 minutes transit.")
        String reasoning,

        @Schema(description = "Environmental factors detected", example = "RAINY / MODERATE_TRAFFIC")
        String weatherOrTrafficFactor
) {}
