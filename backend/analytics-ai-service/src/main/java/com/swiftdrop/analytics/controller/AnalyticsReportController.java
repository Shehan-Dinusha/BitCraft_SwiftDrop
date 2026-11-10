package com.swiftdrop.analytics.controller;

import com.swiftdrop.analytics.dto.*;
import com.swiftdrop.analytics.domain.projection.OrderProjection;
import com.swiftdrop.analytics.security.OperationalReportAccess;
import com.swiftdrop.analytics.service.AnalyticsReportService;
import com.swiftdrop.contracts.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "AnalyticsReports", description = "Operational reports for DISPATCHER and SYSTEM_ADMIN")
@OperationalReportAccess
public class AnalyticsReportController {
    private final AnalyticsReportService reports;
    public AnalyticsReportController(AnalyticsReportService reports) { this.reports = reports; }

    @GetMapping("/deliveries/daily")
    @Operation(summary = "Daily counts for 1 to 90 inclusive Colombo dates")
    public ApiResponse<DailyDeliveryReportResponse> daily(@Valid @ModelAttribute @ParameterObject ReportDateRange range) {
        var days = reports.daily(range.from(), range.to()).stream()
                .map(day -> new DailyDeliveryReportResponse.Day(day.date(), day.created(), day.delivered(), day.failed())).toList();
        return ApiResponse.success(new DailyDeliveryReportResponse(range.from(), range.to(),
                OrderProjection.REPORTING_ZONE.getId(), days), "Daily delivery report retrieved");
    }

    @GetMapping("/success-rate")
    @Operation(summary = "Weighted outcome rates for 1 to 90 inclusive Colombo dates")
    public ApiResponse<DeliverySuccessRateResponse> rates(@Valid @ModelAttribute @ParameterObject ReportDateRange range) {
        var totals = reports.outcomes(range.from(), range.to());
        return ApiResponse.success(new DeliverySuccessRateResponse(range.from(), range.to(), OrderProjection.REPORTING_ZONE.getId(),
                totals.delivered(), totals.failed(), totals.completed(), totals.successRatePercent(), totals.failureRatePercent()),
                "Delivery outcome rates retrieved");
    }

    @GetMapping("/couriers/workload")
    @Operation(summary = "Current workload for known couriers, including zero-workload couriers")
    public ApiResponse<CourierWorkloadReportResponse> workload() {
        var asOf = Instant.now();
        var couriers = reports.workload().stream()
                .map(courier -> new CourierWorkloadReportResponse.Courier(courier.courierId(), courier.activeOrders())).toList();
        return ApiResponse.success(new CourierWorkloadReportResponse(asOf,
                couriers.stream().mapToLong(CourierWorkloadReportResponse.Courier::activeOrders).sum(), couriers),
                "Current courier workload retrieved");
    }
}
