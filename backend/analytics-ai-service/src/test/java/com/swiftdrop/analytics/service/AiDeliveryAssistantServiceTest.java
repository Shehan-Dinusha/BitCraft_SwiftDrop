package com.swiftdrop.analytics.service;

import com.swiftdrop.analytics.ai.DeliveryAssistantAgent;
import com.swiftdrop.analytics.ai.tools.AnomalyRerouteSuggestionTool;
import com.swiftdrop.analytics.ai.tools.CourierEtaCalculationTool;
import com.swiftdrop.analytics.domain.entity.AiDeliveryInsight;
import com.swiftdrop.analytics.domain.entity.AiToolInvocationAudit;
import com.swiftdrop.analytics.domain.repository.AiDeliveryInsightRepository;
import com.swiftdrop.analytics.domain.repository.AiToolInvocationAuditRepository;
import com.swiftdrop.analytics.dto.*;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiDeliveryAssistantServiceTest {

    private CourierEtaCalculationTool etaTool;
    private AnomalyRerouteSuggestionTool rerouteTool;

    @Mock
    private DeliveryAssistantAgent agent;

    @Mock
    private AiDeliveryInsightRepository insightRepository;

    @Mock
    private AiToolInvocationAuditRepository auditRepository;

    private AiDeliveryAssistantService service;

    @BeforeEach
    void setUp() {
        etaTool = new CourierEtaCalculationTool();
        rerouteTool = new AnomalyRerouteSuggestionTool();
        service = new AiDeliveryAssistantService(etaTool, rerouteTool, agent, insightRepository, auditRepository);
    }

    @Test
    @DisplayName("predictDelayRisk calculates low risk for standard speed clear weather")
    void predictDelayRisk_standardSpeedClear() {
        DelayRiskPredictionRequest request = new DelayRiskPredictionRequest(
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.GAMPAHA,
                "STANDARD",
                "CLEAR",
                "LOW"
        );

        DelayRiskPredictionResponse response = service.predictDelayRisk(request);

        assertThat(response).isNotNull();
        assertThat(response.riskLevel()).isEqualTo("LOW");
        assertThat(response.predictedDelayMinutes()).isEqualTo(0);
        assertThat(response.recommendedAction()).isEqualTo("STANDARD_DISPATCH_DISCHARGE");

        verify(insightRepository).save(any(AiDeliveryInsight.class));
    }

    @Test
    @DisplayName("predictDelayRisk flags critical risk for express speed with monsoon storm")
    void predictDelayRisk_expressSevereWeather() {
        DelayRiskPredictionRequest request = new DelayRiskPredictionRequest(
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                "EXPRESS",
                "MONSOON_STORM",
                "SEVERE"
        );

        DelayRiskPredictionResponse response = service.predictDelayRisk(request);

        assertThat(response).isNotNull();
        assertThat(response.riskLevel()).isIn("HIGH", "CRITICAL");
        assertThat(response.predictedDelayMinutes()).isGreaterThan(0);
        verify(insightRepository).save(any(AiDeliveryInsight.class));
    }

    @Test
    @DisplayName("suggestReroute recommends intermediate hub and steps")
    void suggestReroute_anomalyHandling() {
        AnomalyRerouteRequest request = new AnomalyRerouteRequest(
                "SWD-COL-JAF-001",
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.JAFFNA,
                60,
                "FLOOD_WARNING"
        );

        AnomalyRerouteResponse response = service.suggestReroute(request);

        assertThat(response).isNotNull();
        assertThat(response.trackingNumber()).isEqualTo("SWD-COL-JAF-001");
        assertThat(response.suggestedTransitHub()).isEqualTo(SriLankanDistrict.KURUNEGALA);
        assertThat(response.recommendedSpeed()).isEqualTo("EXPRESS");
        assertThat(response.suggestedSteps()).hasSize(3);
        assertThat(response.priorityScore()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("listRecentAudits maps entity to DTO correctly")
    void listRecentAudits_returnsMappedList() {
        AiToolInvocationAudit audit = new AiToolInvocationAudit(
                "CourierEtaCalculationTool", "ADMIN_OPS", "Query test", "input", "output", 15, true
        );
        when(auditRepository.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of(audit));

        List<AiAuditResponse> audits = service.listRecentAudits();

        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().toolName()).isEqualTo("CourierEtaCalculationTool");
        assertThat(audits.getFirst().callerPersona()).isEqualTo("ADMIN_OPS");
    }
}
