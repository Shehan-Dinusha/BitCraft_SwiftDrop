package com.swiftdrop.analytics.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "District-level operational KPI breakdown")
public record DistrictKpiResponse(
        @Schema(description = "Sri Lankan District", example = "COLOMBO")
        SriLankanDistrict district,

        @Schema(description = "Distinct orders created today with this pickup district", example = "4")
        int totalParcels,

        @Schema(description = "SLA measurement unavailable; null", types = {"integer", "null"}, deprecated = true)
        Integer deliveredOnTime,

        @Schema(description = "SLA measurement unavailable; null", types = {"integer", "null"}, deprecated = true)
        Integer breachedSla,

        @Schema(description = "Failed parcel deliveries", example = "2")
        int failedDeliveries,

        @Schema(description = "SLA measurement unavailable; null", types = {"number", "null"}, deprecated = true)
        BigDecimal onTimeRate,

        @Schema(description = "Measured transit duration unavailable; null", types = {"number", "null"}, deprecated = true)
        BigDecimal avgTransitMinutes
) {}
