package com.swiftdrop.analytics.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Courier performance analytics and SLA adherence metrics")
public record CourierPerformanceResponse(
        @Schema(description = "Courier UUID", example = "a0000000-0000-0000-0000-000000000001")
        UUID courierId,

        @Schema(description = "Courier name", example = "Nimal Silva")
        String courierName,

        @Schema(description = "Operating district", example = "COLOMBO")
        SriLankanDistrict currentDistrict,

        @Schema(description = "Total parcels assigned to this courier", example = "150")
        int totalAssigned,

        @Schema(description = "Total delivered parcels", example = "146")
        int deliveredCount,

        @Schema(description = "Failed parcel count", example = "2")
        int failedCount,

        @Schema(description = "Average transit duration in minutes", example = "38.50")
        BigDecimal avgTransitMinutes,

        @Schema(description = "On-time delivery percentage (0-100)", example = "97.33")
        BigDecimal onTimePercentage,

        @Schema(description = "Courier customer rating (0.0-5.0)", example = "4.92")
        BigDecimal customerRating,

        @Schema(description = "Current shift availability status", example = "AVAILABLE")
        String status,

        @Schema(description = "Timestamp of last event")
        Instant lastEventAt
) {}
