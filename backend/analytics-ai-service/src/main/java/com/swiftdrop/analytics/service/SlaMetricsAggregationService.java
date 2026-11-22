package com.swiftdrop.analytics.service;

import com.swiftdrop.analytics.domain.projection.OrderProjection;
import com.swiftdrop.analytics.dto.CourierPerformanceResponse;
import com.swiftdrop.analytics.dto.OperationalKpiSummaryResponse;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Compatibility reads over real projections; no writes, fixtures or invented SLA values. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SlaMetricsAggregationService {
    private final AnalyticsReportService reports;
    private final Clock clock;

    public SlaMetricsAggregationService(AnalyticsReportService reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    /** Today in one district, or on the whole island when {@code district} is null. */
    public OperationalKpiSummaryResponse getKpiSummary(SriLankanDistrict district) {
        LocalDate today = LocalDate.now(clock.withZone(OrderProjection.REPORTING_ZONE));
        var day = reports.daily(today, today, district).getFirst();
        int activeCouriers = Math.toIntExact(reports.workload(district).stream().filter(c -> c.activeOrders() > 0).count());
        return new OperationalKpiSummaryResponse(Math.toIntExact(day.created()), null, null,
                Math.toIntExact(day.failed()), null, null, activeCouriers, reports.districts(today).stream()
                .filter(row -> district == null || row.district() == district).toList());
    }

    public List<CourierPerformanceResponse> listCourierPerformances(SriLankanDistrict district) {
        return reports.couriers(district, null);
    }

    public Optional<CourierPerformanceResponse> getCourierPerformance(UUID courierId, SriLankanDistrict district) {
        return reports.couriers(district, courierId).stream().findFirst();
    }
}
