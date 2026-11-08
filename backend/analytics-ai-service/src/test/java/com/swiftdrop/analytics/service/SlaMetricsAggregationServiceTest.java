package com.swiftdrop.analytics.service;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SlaMetricsAggregationServiceTest {
    private final AnalyticsReportService reports = mock(AnalyticsReportService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T18:30:00Z"), ZoneOffset.UTC);
    private final SlaMetricsAggregationService service = new SlaMetricsAggregationService(reports, clock);
    private final LocalDate today = LocalDate.of(2026, 11, 8);

    @Test
    void emptySummaryHasZeroRealCountsAndUnavailableMeasurements() {
        when(reports.daily(today, today)).thenReturn(List.of(new AnalyticsReportService.DailyStats(today, 0, 0, 0)));
        when(reports.workload()).thenReturn(List.of());
        when(reports.districts(today)).thenReturn(List.of());
        var summary = service.getSystemKpiSummary();
        assertThat(summary.totalParcels()).isZero();
        assertThat(summary.activeCouriers()).isZero();
        assertThat(summary.failedDeliveries()).isZero();
        assertThat(summary.deliveredOnTime()).isNull();
        assertThat(summary.breachedSla()).isNull();
        assertThat(summary.systemOnTimeRate()).isNull();
        assertThat(summary.avgTransitMinutes()).isNull();
        assertThat(summary.districtBreakdowns()).isEmpty();
    }

    @Test
    void summaryUsesColomboDayAndCountsOnlyCouriersWithWork() {
        when(reports.daily(today, today)).thenReturn(List.of(new AnalyticsReportService.DailyStats(today, 4, 2, 1)));
        when(reports.workload()).thenReturn(List.of(new AnalyticsReportService.CourierWorkload(UUID.randomUUID(), 0),
                new AnalyticsReportService.CourierWorkload(UUID.randomUUID(), 1)));
        when(reports.districts(today)).thenReturn(List.of());
        var summary = service.getSystemKpiSummary();
        assertThat(summary.totalParcels()).isEqualTo(4);
        assertThat(summary.failedDeliveries()).isEqualTo(1);
        assertThat(summary.activeCouriers()).isEqualTo(1);
    }

    @Test
    void unknownCourierRemainsMissing() {
        UUID id = UUID.randomUUID();
        when(reports.couriers(null, id)).thenReturn(List.of());
        assertThat(service.getCourierPerformance(id)).isEmpty();
    }

    @Test
    void ratesUseCompletedOutcomesAndAreUndefinedWithoutOutcomes() {
        var totals = new AnalyticsReportService.OutcomeTotals(2, 1);
        assertThat(totals.completed()).isEqualTo(3);
        assertThat(totals.successRatePercent()).isEqualByComparingTo("66.67");
        assertThat(totals.failureRatePercent()).isEqualByComparingTo("33.33");
        assertThat(new AnalyticsReportService.OutcomeTotals(0, 0).successRatePercent()).isNull();
    }
}
