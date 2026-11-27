package com.swiftdrop.webhook.controller;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.exception.DlqRecordNotFoundException;
import com.swiftdrop.webhook.exception.LegacyReplayRetiredException;
import com.swiftdrop.webhook.service.DlqReplayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DlqReplayController.class)
class DlqReplayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DlqReplayService dlqReplayService;

    private final UUID dlqId = UUID.randomUUID();
    private final UUID endpointId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        request.header("X-User-Id", userId.toString()).header("X-User-Role", role);
        return "MERCHANT".equals(role) ? request.header("X-Merchant-Id", merchantId.toString()) : request.header("X-Merchant-Id", "");
    }

    private DeadLetterWebhookResponse record() {
        return new DeadLetterWebhookResponse(dlqId, endpointId, merchantId, "parcel.status.changed",
                "{\"status\":\"DELIVERED\"}", "Connection timeout", AttemptErrorClass.TIMEOUT,
                "Endpoint did not respond before the timeout", 3, false, null, Instant.now());
    }

    @Test
    @DisplayName("GET /health needs no identity")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/dlq/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("requests without gateway identity are rejected with 401")
    void missingIdentityIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/dlq")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/webhooks/dlq/replay-all")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/webhooks/dlq/{dlqId}/replay", dlqId)).andExpect(status().isUnauthorized());
        verifyNoInteractions(dlqReplayService);
    }

    @Test
    @DisplayName("GET list passes filters and caller identity to the service")
    void testListDlqRecords() throws Exception {
        when(dlqReplayService.listDlqRecords(any(), any(), any())).thenReturn(List.of(record()));

        mockMvc.perform(as("MERCHANT", get("/api/v1/webhooks/dlq")).param("unreplayedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dlqId").value(dlqId.toString()));

        ArgumentCaptor<CallerContext> caller = ArgumentCaptor.forClass(CallerContext.class);
        verify(dlqReplayService).listDlqRecords(eq(null), eq(true), caller.capture());
        assertThat(caller.getValue().merchantId()).isEqualTo(merchantId);
        assertThat(caller.getValue().role()).isEqualTo(UserRole.MERCHANT);
    }

    @Test
    @DisplayName("a dispatcher is passed through as DISPATCHER, never elevated, and the refusal is a 403")
    void dispatcherIsRefused() throws Exception {
        when(dlqReplayService.listDlqRecords(any(), any(), any())).thenThrow(new ForbiddenException("Caller role is not permitted"));
        when(dlqReplayService.getDlqRecord(any(), any())).thenThrow(new ForbiddenException("Caller role is not permitted"));

        mockMvc.perform(as("DISPATCHER", get("/api/v1/webhooks/dlq"))).andExpect(status().isForbidden());
        mockMvc.perform(as("DISPATCHER", get("/api/v1/webhooks/dlq/{dlqId}", dlqId))).andExpect(status().isForbidden());

        ArgumentCaptor<CallerContext> caller = ArgumentCaptor.forClass(CallerContext.class);
        verify(dlqReplayService).listDlqRecords(eq(null), eq(false), caller.capture());
        assertThat(caller.getValue().role()).isEqualTo(UserRole.DISPATCHER);
        assertThat(caller.getValue().merchantId()).isNull();
    }

    @Test
    @DisplayName("GET record and another merchant's record (404)")
    void testGetDlqRecord() throws Exception {
        when(dlqReplayService.getDlqRecord(eq(dlqId), any())).thenReturn(record());
        UUID otherId = UUID.randomUUID();
        when(dlqReplayService.getDlqRecord(eq(otherId), any()))
                .thenThrow(new DlqRecordNotFoundException("Dead-letter webhook record not found with ID: " + otherId));

        mockMvc.perform(as("MERCHANT", get("/api/v1/webhooks/dlq/{dlqId}", dlqId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dlqId").value(dlqId.toString()));
        mockMvc.perform(as("MERCHANT", get("/api/v1/webhooks/dlq/{dlqId}", otherId)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MERCHANT", "SYSTEM_ADMIN", "DISPATCHER"})
    @DisplayName("legacy replay and replay-all are retired: 410 Gone, pointing at the failed-delivery API, nothing done")
    void legacyReplayIsGone(String role) throws Exception {
        mockMvc.perform(as(role, post("/api/v1/webhooks/dlq/{dlqId}/replay", dlqId)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.error").value("Gone"))
                .andExpect(jsonPath("$.message").value(LegacyReplayRetiredException.MESSAGE));
        mockMvc.perform(as(role, post("/api/v1/webhooks/dlq/replay-all")).param("merchantId", merchantId.toString()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value(LegacyReplayRetiredException.MESSAGE));

        verifyNoInteractions(dlqReplayService);
    }
}
