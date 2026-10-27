package com.swiftdrop.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.dto.CourierPerformanceResponse;
import com.swiftdrop.analytics.dto.DistrictKpiResponse;
import com.swiftdrop.analytics.dto.OperationalKpiSummaryResponse;
import com.swiftdrop.analytics.service.SlaMetricsAggregationService;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalKpiController.class)
class OperationalKpiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SlaMetricsAggregationService metricsService;

    @Test
    @DisplayName("GET /api/v1/analytics/health returns 200 OK")
    void health_returnsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OperationalKpiController is ready"));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/kpis returns system operational summary")
    void getKpis_returnsSummary() throws Exception {
        OperationalKpiSummaryResponse summary = new OperationalKpiSummaryResponse(
                500, 480, 12, 8,
                new BigDecimal("96.00"),
                new BigDecimal("42.50"),
                15,
                List.of(new DistrictKpiResponse(
                        SriLankanDistrict.COLOMBO, 200, 195, 3, 2,
                        new BigDecimal("97.50"), new BigDecimal("35.00")
                ))
        );

        when(metricsService.getSystemKpiSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalParcels").value(500))
                .andExpect(jsonPath("$.data.deliveredOnTime").value(480))
                .andExpect(jsonPath("$.data.activeCouriers").value(15))
                .andExpect(jsonPath("$.data.districtBreakdowns[0].district").value("COLOMBO"));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/couriers returns list of courier performance metrics")
    void getCouriers_returnsList() throws Exception {
        UUID courierId = UUID.randomUUID();
        CourierPerformanceResponse courier = new CourierPerformanceResponse(
                courierId, "Saman Kumara", SriLankanDistrict.COLOMBO,
                60, 58, 2,
                new BigDecimal("34.50"), new BigDecimal("96.67"), new BigDecimal("4.85"),
                "AVAILABLE", Instant.now()
        );

        when(metricsService.listCourierPerformances(any())).thenReturn(List.of(courier));

        mockMvc.perform(get("/api/v1/analytics/couriers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].courierName").value("Saman Kumara"))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/couriers/{courierId} returns 200 when found and 404 when missing")
    void getCourier_foundAndNotFound() throws Exception {
        UUID courierId = UUID.randomUUID();
        CourierPerformanceResponse courier = new CourierPerformanceResponse(
                courierId, "Saman Kumara", SriLankanDistrict.COLOMBO,
                60, 58, 2,
                new BigDecimal("34.50"), new BigDecimal("96.67"), new BigDecimal("4.85"),
                "AVAILABLE", Instant.now()
        );

        when(metricsService.getCourierPerformance(courierId)).thenReturn(Optional.of(courier));
        when(metricsService.getCourierPerformance(UUID.fromString("00000000-0000-0000-0000-000000000000")))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/analytics/couriers/" + courierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.courierName").value("Saman Kumara"));

        mockMvc.perform(get("/api/v1/analytics/couriers/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/analytics/snapshots/aggregate triggers recalculation")
    void aggregateSnapshots_returnsSummary() throws Exception {
        OperationalKpiSummaryResponse summary = new OperationalKpiSummaryResponse(
                100, 95, 3, 2,
                new BigDecimal("95.00"), new BigDecimal("40.00"), 8, List.of()
        );
        when(metricsService.getSystemKpiSummary()).thenReturn(summary);

        mockMvc.perform(post("/api/v1/analytics/snapshots/aggregate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalParcels").value(100));
    }
}
