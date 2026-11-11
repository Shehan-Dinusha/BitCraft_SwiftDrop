package com.swiftdrop.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.ai.*;
import com.swiftdrop.analytics.ai.tools.*;
import com.swiftdrop.analytics.domain.repository.AiToolInvocationAuditRepository;
import com.swiftdrop.analytics.exception.GlobalExceptionHandler;
import com.swiftdrop.analytics.service.AiDeliveryAssistantService;
import com.swiftdrop.analytics.domain.repository.AiDeliveryInsightRepository;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiChatEndpointTest {
    MockMvc mvc;
    @BeforeEach void setup() {
        var mapper = new ObjectMapper();
        var tools = new ToolExecutor(mock(TrackingClient.class), mock(PricingClient.class), mock(AiToolInvocationAuditRepository.class));
        var chat = new AiChatService(new MockLlmClient(mapper), tools, new ChatSessionStore(), mapper);
        var legacy = new AiDeliveryAssistantController(new AiDeliveryAssistantService(mock(CourierEtaCalculationTool.class),
                mock(AnomalyRerouteSuggestionTool.class), new DeliveryAssistantAgent(chat),
                mock(AiDeliveryInsightRepository.class), mock(AiToolInvocationAuditRepository.class)));
        mvc = MockMvcBuilders.standaloneSetup(new AiChatController(chat), legacy).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void allNonCustomerRolesAreDeniedIncludingLegacyAliases() throws Exception {
        for (String role : new String[] {"SYSTEM_ADMIN", "DISPATCHER", "MERCHANT", "COURIER"}) {
            for (String path : new String[] {"/api/v1/ai/chat", "/api/v1/analytics/ai/agent-query", "/api/v1/ai-delivery-assistant/agent-query"}) {
                String body = path.endsWith("/chat") ? "{\"message\":\"Hello\"}" : "{\"query\":\"Hello\",\"callerPersona\":\"CUSTOMER\"}";
                mvc.perform(post(path).header("X-User-Id", "00000000-0000-0000-0000-000000000001").header("X-User-Role", role)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
            }
        }
    }
    @Test void missingIdentityRejected() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void malformedIdentityRejected() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").header("X-User-Id", "not-uuid").header("X-User-Role", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void customerCanAskForMissingOrigin() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").header("X-User-Id", "00000000-0000-0000-0000-000000000001").header("X-User-Role", "CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"How much to send 2 kg to Kandy?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reply").value(org.hamcrest.Matchers.containsString("origin")))
                .andExpect(jsonPath("$.data.conversationId").isString()).andExpect(jsonPath("$.data.toolCalls").isEmpty());
    }
    @Test void blankAndOversizedMessagesRejected() throws Exception {
        for (String message : new String[] {" ", "x".repeat(2001)}) {
            mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + message + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
    @Test void invalidConversationUuidRejected() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hello\",\"conversationId\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
    }
}
