package com.swiftdrop.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.ai.*;
import com.swiftdrop.analytics.ai.tools.*;
import com.swiftdrop.analytics.domain.repository.AiToolInvocationAuditRepository;
import com.swiftdrop.analytics.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiMcpControllerTest {
    MockMvc mvc;
    TrackingClient tracking;
    PricingClient pricing;
    AiToolInvocationAuditRepository audits;
    ObjectMapper mapper = new ObjectMapper();
    ChatSessionStore limits;
    static final String USER = "00000000-0000-0000-0000-000000000001";

    @BeforeEach void setup() {
        tracking = mock(TrackingClient.class);
        pricing = mock(PricingClient.class);
        audits = mock(AiToolInvocationAuditRepository.class);
        limits = new ChatSessionStore();
        mvc = MockMvcBuilders.standaloneSetup(new AiMcpController(new ToolExecutor(tracking, pricing, audits), limits, mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    MockHttpServletRequestBuilder rpc(String method, String params) {
        return post("/api/v1/ai/mcp").contentType("application/json")
                .accept("application/json", "text/event-stream")
                .header("X-User-Id", USER).header("X-User-Role", "CUSTOMER")
                .header("Authorization", "Bearer test-customer").header("X-Correlation-Id", "mcp-check")
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}");
    }

    @Test void clientInitializesListsAndCallsBothTools() throws Exception {
        mvc.perform(rpc("initialize", "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.protocolVersion").value("2025-03-26"));
        mvc.perform(rpc("tools/list", "{}"))
                .andExpect(jsonPath("$.result.tools.length()").value(2))
                .andExpect(jsonPath("$.result.tools[0].name").value("track_parcel"))
                .andExpect(jsonPath("$.result.tools[1].inputSchema.properties.weightKg.maximum").value(50));
        when(tracking.track(eq("SD-ABCD-1234"), any())).thenReturn(Map.of("outcome", "OK", "status", "IN_TRANSIT"));
        when(pricing.estimate(any(), any(), any(), any())).thenReturn(Map.of("outcome", "OK", "amount", 350));
        for (String params : List.of(
                "{\"name\":\"track_parcel\",\"arguments\":{\"trackingNumber\":\"SD-ABCD-1234\"}}",
                "{\"name\":\"calculate_estimate\",\"arguments\":{\"weightKg\":2,\"originCity\":\"COLOMBO\",\"destinationCity\":\"KANDY\"}}")) {
            mvc.perform(rpc("tools/call", params)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.isError").value(false));
        }
        verify(tracking).track(eq("SD-ABCD-1234"), argThat(context ->
                context.userId().toString().equals(USER) && "Bearer test-customer".equals(context.authorization())
                        && "mcp-check".equals(context.correlationId())));
        verify(audits, times(2)).save(any());
    }

    @Test void invalidAndDisallowedCallsAreAuditedWithoutCallingClients() throws Exception {
        for (String params : List.of(
                "{\"name\":\"track_parcel\",\"arguments\":{\"trackingNumber\":\"bad\"}}",
                "{\"name\":\"calculate_estimate\",\"arguments\":{\"weightKg\":-1,\"originCity\":\"COLOMBO\",\"destinationCity\":\"KANDY\"}}",
                "{\"name\":\"list_customer_phones\",\"arguments\":{}}")) {
            mvc.perform(rpc("tools/call", params)).andExpect(jsonPath("$.result.isError").value(true));
        }
        verifyNoInteractions(tracking, pricing);
        verify(audits, times(3)).save(any());
    }

    @Test void sharedChatBudgetCannotBeBypassedWithMcp() throws Exception {
        for (int i = 0; i < 10; i++) limits.consumeRequest(UUID.fromString(USER));
        mvc.perform(rpc("tools/call", "{\"name\":\"track_parcel\",\"arguments\":{\"trackingNumber\":\"SD-ABCD-1234\"}}"))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(tracking, pricing);
    }

    @Test void missingAndNonCustomerIdentitiesAndBrowserOriginsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/ai/mcp").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("SYSTEM_ADMIN", "DISPATCHER", "MERCHANT", "COURIER")) {
            mvc.perform(rpc("tools/list", "{}").with(request -> {
                request.removeHeader("X-User-Role"); request.addHeader("X-User-Role", role); return request;
            }))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(rpc("tools/list", "{}").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
    }

    @Test void protocolErrorsAndNotificationsHaveProtocolEnvelopes() throws Exception {
        mvc.perform(rpc("unknown", "{}")).andExpect(jsonPath("$.error.code").value(-32601));
        mvc.perform(rpc("tools/call", "{}")).andExpect(jsonPath("$.error.code").value(-32602));
        mvc.perform(rpc("tools/list", "{}").content("{\"jsonrpc\":\"1.0\",\"method\":\"ping\"}"))
                .andExpect(jsonPath("$.error.code").value(-32600));
        mvc.perform(rpc("tools/list", "{}").content("[]")).andExpect(jsonPath("$.error.code").value(-32700));
        mvc.perform(rpc("tools/list", "{}").content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        mvc.perform(get("/api/v1/ai/mcp").header("X-User-Id", USER).header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isMethodNotAllowed());
    }
}
