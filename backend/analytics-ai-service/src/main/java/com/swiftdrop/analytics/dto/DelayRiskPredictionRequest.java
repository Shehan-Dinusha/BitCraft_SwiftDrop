package com.swiftdrop.analytics.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request parameters for AI delay risk prediction")
public record DelayRiskPredictionRequest(
        @NotNull(message = "originDistrict is required")
        @Schema(description = "Pickup district", example = "COLOMBO")
        SriLankanDistrict originDistrict,

        @NotNull(message = "destinationDistrict is required")
        @Schema(description = "Delivery destination district", example = "KANDY")
        SriLankanDistrict destinationDistrict,

        @Schema(description = "Delivery service level speed", example = "STANDARD")
        String deliverySpeed,

        @Schema(description = "Current weather condition", example = "RAINY")
        String weatherCondition,

        @Schema(description = "Traffic congestion level (LOW, MODERATE, SEVERE)", example = "MODERATE")
        String trafficCongestion
) {}
