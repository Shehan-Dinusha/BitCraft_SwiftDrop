package com.swiftdrop.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.dto.*;
import com.swiftdrop.analytics.service.AiDeliveryAssistantService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiDeliveryAssistantController.class)
class AiDeliveryAssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiDeliveryAssistantService assistantService;

    @Test
    @DisplayName("GET /api/v1/analytics/ai/health returns 200 OK")
    void health_returnsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("AiDeliveryAssistantController is ready"));
    }

    @Test
    @DisplayName("POST /api/v1/analytics/ai/predict-delay returns risk score and actions")
    void predictDelayRisk_validRequest() throws Exception {
        DelayRiskPredictionRequest request = new DelayRiskPredictionRequest(
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                "EXPRESS",
                "RAINY",
                "MODERATE"
        );

        DelayRiskPredictionResponse response = new DelayRiskPredictionResponse(
                UUID.randomUUID(),
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                "EXPRESS",
                "MEDIUM",
                new BigDecimal("35.00"),
                20,
                "MONITOR_TRANSIT_SPEED",
                "Rain adds +20%",
                "RAINY / MODERATE"
        );

        when(assistantService.predictDelayRisk(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/analytics/ai/predict-delay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.predictedDelayMinutes").value(20));
    }

    @Test
    @DisplayName("POST /api/v1/analytics/ai/reroute-suggestions returns waypoint reroute steps")
    void suggestReroute_validRequest() throws Exception {
        AnomalyRerouteRequest request = new AnomalyRerouteRequest(
                "SWD-12345",
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.JAFFNA,
                45,
                "ROAD_WORK"
        );

        AnomalyRerouteResponse response = new AnomalyRerouteResponse(
                "SWD-12345",
                SriLankanDistrict.KURUNEGALA,
                "EXPRESS",
                7,
                List.of("Reroute via Kurunegala hub", "Hand off to regional priority"),
                "Saves 35 minutes"
        );

        when(assistantService.suggestReroute(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/analytics/ai/reroute-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggestedTransitHub").value("KURUNEGALA"))
                .andExpect(jsonPath("$.data.priorityScore").value(7));
    }

    @Test
    @DisplayName("POST /api/v1/analytics/ai/agent-query processes natural language prompt")
    void queryAgent_validRequest() throws Exception {
        AssistantQueryRequest request = new AssistantQueryRequest(
                "What is the transit ETA for SWD-001?",
                "ADMIN_OPS",
                "SWD-001"
        );

        AssistantQueryResponse response = new AssistantQueryResponse(
                "Estimated transit time is 45 minutes",
                "ETA_INQUIRY",
                0.95,
                List.of("NOTIFY_CUSTOMER"),
                List.of("CourierEtaCalculationTool")
        );

        when(assistantService.processAgentQuery(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/analytics/ai/agent-query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.detectedIntent").value("ETA_INQUIRY"))
                .andExpect(jsonPath("$.data.answer").value("Estimated transit time is 45 minutes"));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/ai/audits returns list of tool invocation audits")
    void getAudits_returnsList() throws Exception {
        AiAuditResponse audit = new AiAuditResponse(
                UUID.randomUUID(),
                "CourierEtaCalculationTool",
                "ADMIN_OPS",
                "Calculate ETA",
                "45 mins",
                12,
                true,
                Instant.now()
        );

        when(assistantService.listRecentAudits()).thenReturn(List.of(audit));

        mockMvc.perform(get("/api/v1/analytics/ai/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].toolName").value("CourierEtaCalculationTool"));
    }
}
