package com.swiftdrop.analytics.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "District-level operational KPI breakdown")
public record DistrictKpiResponse(
        @Schema(description = "Sri Lankan District", example = "COLOMBO")
        SriLankanDistrict district,

        @Schema(description = "Total parcels processed in this district", example = "142")
        int totalParcels,

        @Schema(description = "Parcels delivered within SLA", example = "138")
        int deliveredOnTime,

        @Schema(description = "Parcels that breached SLA", example = "2")
        int breachedSla,

        @Schema(description = "Failed parcel deliveries", example = "2")
        int failedDeliveries,

        @Schema(description = "On-time delivery percentage (0-100)", example = "97.18")
        BigDecimal onTimeRate,

        @Schema(description = "Average transit duration in minutes", example = "42.50")
        BigDecimal avgTransitMinutes
) {}
