package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "System-wide operational KPI overview")
public record OperationalKpiSummaryResponse(
        @Schema(description = "Total parcels processed", example = "1250")
        int totalParcels,

        @Schema(description = "Total parcels delivered on time", example = "1198")
        int deliveredOnTime,

        @Schema(description = "Total parcels that breached SLA", example = "32")
        int breachedSla,

        @Schema(description = "Total delivery failures", example = "20")
        int failedDeliveries,

        @Schema(description = "System-wide on-time rate percentage", example = "95.84")
        BigDecimal systemOnTimeRate,

        @Schema(description = "Average delivery transit time across island in minutes", example = "48.20")
        BigDecimal avgTransitMinutes,

        @Schema(description = "Count of active couriers", example = "35")
        int activeCouriers,

        @Schema(description = "District-by-district KPI breakdown")
        List<DistrictKpiResponse> districtBreakdowns
) {}
