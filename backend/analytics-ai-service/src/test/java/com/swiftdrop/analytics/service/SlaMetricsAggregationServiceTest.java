package com.swiftdrop.analytics.service;

import com.swiftdrop.analytics.domain.entity.CourierPerformanceMetric;
import com.swiftdrop.analytics.domain.entity.OperationalSlaSnapshot;
import com.swiftdrop.analytics.domain.repository.CourierPerformanceMetricRepository;
import com.swiftdrop.analytics.domain.repository.OperationalSlaSnapshotRepository;
import com.swiftdrop.analytics.dto.CourierPerformanceResponse;
import com.swiftdrop.analytics.dto.OperationalKpiSummaryResponse;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaMetricsAggregationServiceTest {

    @Mock
    private OperationalSlaSnapshotRepository slaRepository;

    @Mock
    private CourierPerformanceMetricRepository courierRepository;

    private SlaMetricsAggregationService service;

    @BeforeEach
    void setUp() {
        service = new SlaMetricsAggregationService(slaRepository, courierRepository);
    }

    @Test
    @DisplayName("getSystemKpiSummary seeds initial defaults when no snapshots exist today")
    void getSystemKpiSummary_seedsDefaultsWhenEmpty() {
        when(slaRepository.findAllBySnapshotDate(any(LocalDate.class))).thenReturn(List.of());
        when(slaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(courierRepository.count()).thenReturn(0L);

        OperationalKpiSummaryResponse summary = service.getSystemKpiSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.districtBreakdowns()).isNotEmpty();
        assertThat(summary.totalParcels()).isGreaterThan(0);
        assertThat(summary.activeCouriers()).isEqualTo(12);
        verify(slaRepository).saveAll(any());
    }

    @Test
    @DisplayName("getSystemKpiSummary aggregates existing snapshots correctly")
    void getSystemKpiSummary_aggregatesExistingSnapshots() {
        OperationalSlaSnapshot s1 = new OperationalSlaSnapshot(SriLankanDistrict.COLOMBO, LocalDate.now());
        s1.setTotalParcels(100);
        s1.setDeliveredOnTime(95);
        s1.setBreachedSla(3);
        s1.setFailedDeliveries(2);
        s1.setAvgTransitTimeMinutes(new BigDecimal("35.00"));
        s1.setOnTimeRate(new BigDecimal("95.00"));

        when(slaRepository.findAllBySnapshotDate(any(LocalDate.class))).thenReturn(List.of(s1));
        when(courierRepository.count()).thenReturn(5L);

        OperationalKpiSummaryResponse summary = service.getSystemKpiSummary();

        assertThat(summary.totalParcels()).isEqualTo(100);
        assertThat(summary.deliveredOnTime()).isEqualTo(95);
        assertThat(summary.breachedSla()).isEqualTo(3);
        assertThat(summary.failedDeliveries()).isEqualTo(2);
        assertThat(summary.systemOnTimeRate()).isEqualByComparingTo(new BigDecimal("95.00"));
        assertThat(summary.activeCouriers()).isEqualTo(5);
    }

    @Test
    @DisplayName("recordDeliveryEvent increments deliveredOnTime on DELIVERED")
    void recordDeliveryEvent_deliveredSuccess() {
        OperationalSlaSnapshot existing = new OperationalSlaSnapshot(SriLankanDistrict.COLOMBO, LocalDate.now());
        existing.setTotalParcels(10);
        existing.setDeliveredOnTime(9);

        when(slaRepository.findByDistrictAndSnapshotDate(eq(SriLankanDistrict.COLOMBO), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "SWD-123", UUID.randomUUID(), UUID.randomUUID(),
                PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.DELIVERED, null,
                SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, null, Instant.now()
        );

        service.recordDeliveryEvent(event);

        ArgumentCaptor<OperationalSlaSnapshot> captor = ArgumentCaptor.forClass(OperationalSlaSnapshot.class);
        verify(slaRepository).save(captor.capture());
        OperationalSlaSnapshot saved = captor.getValue();
        assertThat(saved.getTotalParcels()).isEqualTo(11);
        assertThat(saved.getDeliveredOnTime()).isEqualTo(10);
    }

    @Test
    @DisplayName("recordDeliveryEvent increments failedDeliveries on CANCELLED")
    void recordDeliveryEvent_failedIncrements() {
        OperationalSlaSnapshot existing = new OperationalSlaSnapshot(SriLankanDistrict.GAMPAHA, LocalDate.now());
        when(slaRepository.findByDistrictAndSnapshotDate(eq(SriLankanDistrict.GAMPAHA), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "SWD-456", UUID.randomUUID(), UUID.randomUUID(),
                PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.CANCELLED, null,
                SriLankanDistrict.GAMPAHA, SriLankanDistrict.GAMPAHA, null, Instant.now()
        );

        service.recordDeliveryEvent(event);

        ArgumentCaptor<OperationalSlaSnapshot> captor = ArgumentCaptor.forClass(OperationalSlaSnapshot.class);
        verify(slaRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedDeliveries()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordCourierAssigned updates courier metric to BUSY")
    void recordCourierAssigned_upsertsPerformance() {
        UUID courierId = UUID.randomUUID();
        when(courierRepository.findByCourierId(courierId)).thenReturn(Optional.empty());

        CourierAssignedEvent event = new CourierAssignedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), courierId,
                "Kasun Perera", 15, Instant.now(), Instant.now()
        );

        service.recordCourierAssigned(event);

        ArgumentCaptor<CourierPerformanceMetric> captor = ArgumentCaptor.forClass(CourierPerformanceMetric.class);
        verify(courierRepository).save(captor.capture());
        CourierPerformanceMetric saved = captor.getValue();
        assertThat(saved.getCourierId()).isEqualTo(courierId);
        assertThat(saved.getCourierName()).isEqualTo("Kasun Perera");
        assertThat(saved.getTotalAssigned()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo("BUSY");
    }

    @Test
    @DisplayName("listCourierPerformances filters by district correctly")
    void listCourierPerformances_filtersByDistrict() {
        CourierPerformanceMetric m = new CourierPerformanceMetric(UUID.randomUUID(), "Nimal", SriLankanDistrict.KANDY);
        when(courierRepository.findAllByCurrentDistrict(SriLankanDistrict.KANDY)).thenReturn(List.of(m));

        List<CourierPerformanceResponse> responses = service.listCourierPerformances(SriLankanDistrict.KANDY);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().courierName()).isEqualTo("Nimal");
        assertThat(responses.getFirst().currentDistrict()).isEqualTo(SriLankanDistrict.KANDY);
    }
}
