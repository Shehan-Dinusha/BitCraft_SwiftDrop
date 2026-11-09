package com.swiftdrop.analytics.controller;

import com.swiftdrop.analytics.dto.CourierPerformanceResponse;
import com.swiftdrop.analytics.dto.OperationalKpiSummaryResponse;
import com.swiftdrop.analytics.service.SlaMetricsAggregationService;
import com.swiftdrop.analytics.security.OperationalReportAccess;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/analytics", "/api/v1/operational-kpi"})
@Tag(name = "OperationalKpi", description = "Operational KPI and SLA Metrics API")
public class OperationalKpiController {

    private final SlaMetricsAggregationService metricsService;

    public OperationalKpiController(SlaMetricsAggregationService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint for operational analytics")
    public ApiResponse<String> health() {
        return ApiResponse.success("OperationalKpiController is ready");
    }

    @GetMapping("/kpis")
    @OperationalReportAccess
    @Operation(summary = "Retrieve system-wide and district-level operational KPIs")
    public ApiResponse<OperationalKpiSummaryResponse> getKpis() {
        return ApiResponse.success(metricsService.getSystemKpiSummary(), "Operational KPIs retrieved successfully");
    }

    @GetMapping("/couriers")
    @OperationalReportAccess
    @Operation(summary = "List courier performance analytics across districts")
    public ApiResponse<List<CourierPerformanceResponse>> getCouriers(
            @RequestParam(required = false) SriLankanDistrict district) {
        return ApiResponse.success(metricsService.listCourierPerformances(district), "Courier performance metrics retrieved");
    }

    @GetMapping("/couriers/{courierId}")
    @OperationalReportAccess
    @Operation(summary = "Get performance analytics for a specific courier")
    public ResponseEntity<ApiResponse<CourierPerformanceResponse>> getCourier(
            @PathVariable UUID courierId) {
        return metricsService.getCourierPerformance(courierId)
                .map(data -> ResponseEntity.ok(ApiResponse.success(data, "Courier performance found")))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/snapshots/aggregate")
    @OperationalReportAccess
    @Operation(summary = "Read current persisted analytics projections")
    public ApiResponse<OperationalKpiSummaryResponse> aggregateSnapshots() {
        return ApiResponse.success(metricsService.getSystemKpiSummary(), "Current analytics projections retrieved");
    }
}
