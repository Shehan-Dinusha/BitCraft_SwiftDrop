package com.swiftdrop.analytics.controller;

import com.swiftdrop.analytics.dto.*;
import com.swiftdrop.analytics.domain.projection.OrderProjection;
import com.swiftdrop.analytics.security.OperationalReportAccess;
import com.swiftdrop.analytics.security.ReportDistrict;
import com.swiftdrop.analytics.service.AnalyticsReportService;
import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "AnalyticsReports", description = "Operational reports. A DISPATCHER sees their own district; a SYSTEM_ADMIN sees every district or the one in district")
@OperationalReportAccess
public class AnalyticsReportController {
    private final AnalyticsReportService reports;
    public AnalyticsReportController(AnalyticsReportService reports) { this.reports = reports; }

    @GetMapping("/deliveries/daily")
    @Operation(summary = "Daily counts for 1 to 90 inclusive Colombo dates")
    public ApiResponse<DailyDeliveryReportResponse> daily(@Valid @ModelAttribute @ParameterObject ReportDateRange range,
            @RequestParam(required = false) SriLankanDistrict district, HttpServletRequest request) {
        var days = reports.daily(range.from(), range.to(), ReportDistrict.of(request, district)).stream()
                .map(day -> new DailyDeliveryReportResponse.Day(day.date(), day.created(), day.delivered(), day.failed())).toList();
        return ApiResponse.success(new DailyDeliveryReportResponse(range.from(), range.to(),
                OrderProjection.REPORTING_ZONE.getId(), days), "Daily delivery report retrieved");
    }

    @GetMapping("/success-rate")
    @Operation(summary = "Weighted outcome rates for 1 to 90 inclusive Colombo dates")
    public ApiResponse<DeliverySuccessRateResponse> rates(@Valid @ModelAttribute @ParameterObject ReportDateRange range,
            @RequestParam(required = false) SriLankanDistrict district, HttpServletRequest request) {
        var totals = reports.outcomes(range.from(), range.to(), ReportDistrict.of(request, district));
        return ApiResponse.success(new DeliverySuccessRateResponse(range.from(), range.to(), OrderProjection.REPORTING_ZONE.getId(),
                totals.delivered(), totals.failed(), totals.completed(), totals.successRatePercent(), totals.failureRatePercent()),
                "Delivery outcome rates retrieved");
    }

    @GetMapping("/couriers/workload")
    @Operation(summary = "Current workload for known couriers, including zero-workload couriers")
    public ApiResponse<CourierWorkloadReportResponse> workload(
            @RequestParam(required = false) SriLankanDistrict district, HttpServletRequest request) {
        var asOf = Instant.now();
        var couriers = reports.workload(ReportDistrict.of(request, district)).stream()
                .map(courier -> new CourierWorkloadReportResponse.Courier(courier.courierId(), courier.activeOrders())).toList();
        return ApiResponse.success(new CourierWorkloadReportResponse(asOf,
                couriers.stream().mapToLong(CourierWorkloadReportResponse.Courier::activeOrders).sum(), couriers),
                "Current courier workload retrieved");
    }
}
