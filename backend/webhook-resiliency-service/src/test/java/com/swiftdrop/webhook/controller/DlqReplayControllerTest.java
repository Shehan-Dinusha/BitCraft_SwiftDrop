package com.swiftdrop.webhook.controller;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.dto.ReplayDlqResponse;
import com.swiftdrop.webhook.exception.DlqRecordNotFoundException;
import com.swiftdrop.webhook.service.DlqReplayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    @DisplayName("dispatcher identity is passed through as DISPATCHER, never elevated")
    void dispatcherRoleIsPreserved() throws Exception {
        when(dlqReplayService.listDlqRecords(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(as("DISPATCHER", get("/api/v1/webhooks/dlq"))).andExpect(status().isOk());

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

    @Test
    @DisplayName("POST replay by the owner")
    void testReplayRecord() throws Exception {
        when(dlqReplayService.replayDlqRecord(eq(dlqId), any())).thenReturn(
                new ReplayDlqResponse(dlqId, "SUCCESS", 200, "Webhook replayed and accepted", Instant.now()));

        mockMvc.perform(as("MERCHANT", post("/api/v1/webhooks/dlq/{dlqId}/replay", dlqId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("a refused replay (e.g. by a dispatcher) is a 403")
    void forbiddenReplayIsMapped() throws Exception {
        when(dlqReplayService.replayDlqRecord(eq(dlqId), any())).thenThrow(new ForbiddenException("Caller role is not permitted"));
        when(dlqReplayService.replayAllUnreplayed(any(), any())).thenThrow(new ForbiddenException("Caller role is not permitted"));

        mockMvc.perform(as("DISPATCHER", post("/api/v1/webhooks/dlq/{dlqId}/replay", dlqId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as("DISPATCHER", post("/api/v1/webhooks/dlq/replay-all")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST replay-all passes the optional filter and caller")
    void testReplayAll() throws Exception {
        when(dlqReplayService.replayAllUnreplayed(any(), any())).thenReturn(List.of(
                new ReplayDlqResponse(dlqId, "SUCCESS", 200, "Webhook replayed and accepted", Instant.now())));

        mockMvc.perform(as("SYSTEM_ADMIN", post("/api/v1/webhooks/dlq/replay-all")).param("merchantId", merchantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));

        ArgumentCaptor<CallerContext> caller = ArgumentCaptor.forClass(CallerContext.class);
        verify(dlqReplayService).replayAllUnreplayed(eq(merchantId), caller.capture());
        assertThat(caller.getValue().role()).isEqualTo(UserRole.SYSTEM_ADMIN);
    }
}
