package com.swiftdrop.analytics.ai;

import com.swiftdrop.analytics.ai.tools.AnomalyRerouteSuggestionTool;
import com.swiftdrop.analytics.ai.tools.CourierEtaCalculationTool;
import com.swiftdrop.analytics.ai.tools.OrderLookupTool;
import com.swiftdrop.analytics.domain.repository.AiToolInvocationAuditRepository;
import com.swiftdrop.analytics.dto.AssistantQueryRequest;
import com.swiftdrop.analytics.dto.AssistantQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryAssistantAgentTest {

    private CourierEtaCalculationTool etaTool;
    private AnomalyRerouteSuggestionTool rerouteTool;
    private OrderLookupTool orderLookupTool;

    @Mock
    private AiToolInvocationAuditRepository auditRepository;

    private DeliveryAssistantAgent agent;

    @BeforeEach
    void setUp() {
        etaTool = new CourierEtaCalculationTool();
        rerouteTool = new AnomalyRerouteSuggestionTool();
        orderLookupTool = new OrderLookupTool();
        agent = new DeliveryAssistantAgent(etaTool, rerouteTool, orderLookupTool, auditRepository);
    }

    @Test
    @DisplayName("processQuery detects ETA_INQUIRY intent and invokes ETA tool")
    void processQuery_etaInquiry() {
        AssistantQueryRequest request = new AssistantQueryRequest(
                "What is the estimated delivery time for my parcel?",
                "CUSTOMER",
                "SWD-COL-001"
        );

        AssistantQueryResponse response = agent.processQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.detectedIntent()).isEqualTo("ETA_INQUIRY");
        assertThat(response.toolInvocations()).contains("CourierEtaCalculationTool");
        assertThat(response.suggestedActions()).isNotEmpty();
        verify(auditRepository).save(any());
    }

    @Test
    @DisplayName("processQuery detects REROUTE_SUGGESTION intent and invokes reroute tool")
    void processQuery_rerouteIntent() {
        AssistantQueryRequest request = new AssistantQueryRequest(
                "Heavy traffic and road blocked along route, need reroute suggestion",
                "ADMIN_OPS",
                "SWD-COL-002"
        );

        AssistantQueryResponse response = agent.processQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.detectedIntent()).isEqualTo("REROUTE_SUGGESTION");
        assertThat(response.toolInvocations()).contains("AnomalyRerouteSuggestionTool");
        verify(auditRepository).save(any());
    }

    @Test
    @DisplayName("processQuery detects TRACKING_STATUS intent")
    void processQuery_trackingStatus() {
        AssistantQueryRequest request = new AssistantQueryRequest(
                "Where is my package right now?",
                "CUSTOMER",
                "SWD-GAL-003"
        );

        AssistantQueryResponse response = agent.processQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.detectedIntent()).isEqualTo("TRACKING_STATUS");
        assertThat(response.toolInvocations()).contains("OrderLookupTool");
        verify(auditRepository).save(any());
    }

    @Test
    @DisplayName("processQuery falls back to GENERAL_LOGISTICS_ASSISTANCE")
    void processQuery_generalPrompt() {
        AssistantQueryRequest request = new AssistantQueryRequest(
                "Hello, how is the fleet running today?",
                "ADMIN_OPS",
                null
        );

        AssistantQueryResponse response = agent.processQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.detectedIntent()).isEqualTo("GENERAL_LOGISTICS_ASSISTANCE");
        verify(auditRepository).save(any());
    }
}
