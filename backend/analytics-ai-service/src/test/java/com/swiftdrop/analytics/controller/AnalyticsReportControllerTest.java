package com.swiftdrop.analytics.controller;

import com.swiftdrop.analytics.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AnalyticsReportController.class, OperationalKpiController.class, AiDeliveryAssistantController.class})
class AnalyticsReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AnalyticsReportService reports;
    @MockitoBean SlaMetricsAggregationService metrics;
    @MockitoBean AiDeliveryAssistantService assistant;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 8);

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String role) {
        return request.header("X-User-Id", "00000000-0000-0000-0000-000000000001").header("X-User-Role", role);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISPATCHER", "SYSTEM_ADMIN"})
    void operationalRolesCanReadExactCounts(String role) throws Exception {
        when(reports.daily(DATE, DATE)).thenReturn(List.of(new AnalyticsReportService.DailyStats(DATE, 4, 2, 1)));
        mvc.perform(as(get("/api/v1/analytics/deliveries/daily?from=2026-11-08&to=2026-11-08"), role))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.timezone").value("Asia/Colombo"))
                .andExpect(jsonPath("$.data.days[0].created").value(4))
                .andExpect(jsonPath("$.data.days[0].delivered").value(2));
        when(reports.outcomes(DATE, DATE)).thenReturn(new AnalyticsReportService.OutcomeTotals(2, 1));
        mvc.perform(as(get("/api/v1/analytics/success-rate?from=2026-11-08&to=2026-11-08"), role))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(3))
                .andExpect(jsonPath("$.data.successRatePercent").value(66.67))
                .andExpect(jsonPath("$.data.failureRatePercent").value(33.33));
    }

    @Test void emptyRatesAreExplicitNullsAndWorkloadIsEmpty() throws Exception {
        when(reports.outcomes(DATE, DATE)).thenReturn(new AnalyticsReportService.OutcomeTotals(0, 0));
        mvc.perform(as(get("/api/v1/analytics/success-rate?from=2026-11-08&to=2026-11-08"), "DISPATCHER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(0))
                .andExpect(jsonPath("$.data.successRatePercent").value(nullValue()))
                .andExpect(jsonPath("$.data.failureRatePercent").value(nullValue()));
        mvc.perform(as(get("/api/v1/analytics/couriers/workload"), "DISPATCHER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalActiveOrders").value(0))
                .andExpect(jsonPath("$.data.couriers").isEmpty());
    }

    @Test void staticWorkloadRouteIncludesIdleCouriersAndTotals() throws Exception {
        when(reports.workload()).thenReturn(List.of(new AnalyticsReportService.CourierWorkload(UUID.randomUUID(), 0),
                new AnalyticsReportService.CourierWorkload(UUID.randomUUID(), 1)));
        mvc.perform(as(get("/api/v1/analytics/couriers/workload"), "SYSTEM_ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalActiveOrders").value(1))
                .andExpect(jsonPath("$.data.couriers[0].activeOrders").value(0))
                .andExpect(jsonPath("$.data.asOf").exists());
        verifyNoInteractions(metrics);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?from=2026-11-08", "?from=bad&to=2026-11-08",
            "?from=2026-02-30&to=2026-03-01", "?from=2026-11-09&to=2026-11-08",
            "?from=2026-01-01&to=2026-04-01"})
    void invalidDatesNeverReachDatabase(String query) throws Exception {
        for (var path : List.of("/deliveries/daily", "/success-rate")) {
            mvc.perform(as(get("/api/v1/analytics" + path + query), "DISPATCHER"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        verifyNoInteractions(reports);
    }

    @Test void exactlyNinetyInclusiveDaysIsAllowed() throws Exception {
        mvc.perform(as(get("/api/v1/analytics/deliveries/daily?from=2026-01-01&to=2026-03-31"), "DISPATCHER"))
                .andExpect(status().isOk());
        verify(reports).daily(LocalDate.of(2026,1,1), LocalDate.of(2026,3,31));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "COURIER", "UNKNOWN", "ADMIN_OPS"})
    void everyOperationalRouteAndAliasRejectsDisallowedRoles(String role) throws Exception {
        for (String path : List.of("/analytics/deliveries/daily", "/analytics/success-rate", "/analytics/couriers/workload",
                "/analytics/kpis", "/analytics/couriers", "/analytics/couriers/00000000-0000-0000-0000-000000000001",
                "/operational-kpi/kpis", "/operational-kpi/couriers", "/operational-kpi/couriers/00000000-0000-0000-0000-000000000001",
                "/analytics/ai/audits", "/ai-delivery-assistant/audits")) {
            mvc.perform(as(get("/api/v1" + path), role)).andExpect(status().isForbidden());
        }
        for (String path : List.of("/analytics/snapshots/aggregate", "/operational-kpi/snapshots/aggregate",
                "/analytics/ai/predict-delay", "/analytics/ai/reroute-suggestions",
                "/ai-delivery-assistant/predict-delay", "/ai-delivery-assistant/reroute-suggestions")) {
            mvc.perform(as(post("/api/v1" + path).contentType("application/json").content("{\"callerPersona\":\"SYSTEM_ADMIN\"}"), role))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(reports, metrics, assistant);
    }

    @Test void missingOrMalformedIdentityReturns401() throws Exception {
        mvc.perform(get("/api/v1/analytics/couriers/workload")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/analytics/kpis").header("X-User-Role", "SYSTEM_ADMIN").header("X-User-Id", "invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/analytics/kpis").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reports, metrics, assistant);
    }

    @Test void unexpectedDatabaseErrorsAreSafe500s() throws Exception {
        when(reports.workload()).thenThrow(new IllegalStateException("SQL secret"));
        mvc.perform(as(get("/api/v1/analytics/couriers/workload").header("X-Correlation-Id", "report-test"), "SYSTEM_ADMIN"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("Unable to process request"))
                .andExpect(jsonPath("$.path").value("/api/v1/analytics/couriers/workload"))
                .andExpect(jsonPath("$.correlationId").value("report-test"));
    }
}
