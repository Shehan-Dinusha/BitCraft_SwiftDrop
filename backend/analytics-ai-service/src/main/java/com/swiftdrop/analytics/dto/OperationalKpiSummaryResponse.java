package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "System-wide operational KPI overview")
public record OperationalKpiSummaryResponse(
        @Schema(description = "Distinct orders created today in Asia/Colombo", example = "4")
        int totalParcels,

        @Schema(description = "Unavailable until an SLA deadline contract exists; null", types = {"integer", "null"}, deprecated = true)
        Integer deliveredOnTime,

        @Schema(description = "Unavailable until an SLA deadline contract exists; null", types = {"integer", "null"}, deprecated = true)
        Integer breachedSla,

        @Schema(description = "Distinct final FAILED outcomes today in Asia/Colombo; cancellations excluded", example = "1")
        int failedDeliveries,

        @Schema(description = "Unavailable without measured SLA deadlines; null", types = {"number", "null"}, deprecated = true)
        BigDecimal systemOnTimeRate,

        @Schema(description = "Unavailable without an agreed measured transit interval; null", types = {"number", "null"}, deprecated = true)
        BigDecimal avgTransitMinutes,

        @Schema(description = "Known couriers with at least one current nonterminal order", example = "1")
        int activeCouriers,

        @Schema(description = "District-by-district KPI breakdown")
        List<DistrictKpiResponse> districtBreakdowns
) {}
