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

        @Schema(description = "Latest observed parcel district; null if unknown, not registered operating district", types = {"string", "null"})
        SriLankanDistrict currentDistrict,

        @Schema(description = "Distinct orders ever observed assigned to this courier", example = "3")
        int totalAssigned,

        @Schema(description = "Total delivered parcels", example = "146")
        int deliveredCount,

        @Schema(description = "Failed parcel count", example = "2")
        int failedCount,

        @Schema(description = "Measured transit interval unavailable; null", types = {"number", "null"}, deprecated = true)
        BigDecimal avgTransitMinutes,

        @Schema(description = "SLA measurement unavailable; null", types = {"number", "null"}, deprecated = true)
        BigDecimal onTimePercentage,

        @Schema(description = "Rating events unavailable; null", types = {"number", "null"}, deprecated = true)
        BigDecimal customerRating,

        @Schema(description = "Projected workload state: BUSY or NO_ACTIVE_ORDERS; not shift availability", example = "BUSY")
        String status,

        @Schema(description = "Latest observed assignment business time")
        Instant lastEventAt
) {}
