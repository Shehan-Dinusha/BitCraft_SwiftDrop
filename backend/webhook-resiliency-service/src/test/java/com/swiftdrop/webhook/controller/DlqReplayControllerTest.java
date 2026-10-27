package com.swiftdrop.webhook.controller;

import com.swiftdrop.webhook.dto.DeadLetterWebhookResponse;
import com.swiftdrop.webhook.dto.ReplayDlqResponse;
import com.swiftdrop.webhook.service.DlqReplayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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

    @Test
    @DisplayName("GET /api/v1/webhooks/dlq/health should return 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/dlq/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/webhooks/dlq should return DLQ records list")
    void testListDlqRecords() throws Exception {
        DeadLetterWebhookResponse record = new DeadLetterWebhookResponse(
                dlqId,
                endpointId,
                merchantId,
                "parcel.status.changed",
                "{\"status\":\"DELIVERED\"}",
                "Connection timeout",
                3,
                false,
                null,
                Instant.now()
        );

        when(dlqReplayService.listDlqRecords(eq(merchantId), eq(false))).thenReturn(List.of(record));

        mockMvc.perform(get("/api/v1/webhooks/dlq")
                        .param("merchantId", merchantId.toString())
                        .param("unreplayedOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].dlqId").value(dlqId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/webhooks/dlq/{dlqId} should return single DLQ record")
    void testGetDlqRecord() throws Exception {
        DeadLetterWebhookResponse record = new DeadLetterWebhookResponse(
                dlqId,
                endpointId,
                merchantId,
                "parcel.status.changed",
                "{\"status\":\"DELIVERED\"}",
                "Connection timeout",
                3,
                false,
                null,
                Instant.now()
        );

        when(dlqReplayService.getDlqRecord(dlqId)).thenReturn(record);

        mockMvc.perform(get("/api/v1/webhooks/dlq/{dlqId}", dlqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dlqId").value(dlqId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/dlq/{dlqId}/replay should trigger manual redelivery")
    void testReplayRecord() throws Exception {
        ReplayDlqResponse response = new ReplayDlqResponse(
                dlqId,
                "SUCCESS",
                200,
                "Webhook replayed and accepted",
                Instant.now()
        );

        when(dlqReplayService.replayDlqRecord(dlqId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/webhooks/dlq/{dlqId}/replay", dlqId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/dlq/replay-all should execute batch replay")
    void testReplayAll() throws Exception {
        ReplayDlqResponse response = new ReplayDlqResponse(
                dlqId,
                "SUCCESS",
                200,
                "Webhook replayed and accepted",
                Instant.now()
        );

        when(dlqReplayService.replayAllUnreplayed(merchantId)).thenReturn(List.of(response));

        mockMvc.perform(post("/api/v1/webhooks/dlq/replay-all")
                        .param("merchantId", merchantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
    }
}
