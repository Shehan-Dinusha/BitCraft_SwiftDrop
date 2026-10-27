package com.swiftdrop.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationLogController.class)
class NotificationLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MultiChannelNotificationDispatcher dispatcher;

    private final UUID recipientId = UUID.randomUUID();
    private final UUID logId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/notifications/logs/health should return 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/logs/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/logs should return list of logs")
    void testListLogs() throws Exception {
        NotificationDispatchLogResponse log1 = new NotificationDispatchLogResponse(
                logId,
                recipientId,
                "SMS",
                "PARCEL_PICKED_UP",
                "+94771234567",
                "Picked Up",
                "Your parcel was picked up",
                "SENT",
                null,
                Instant.now(),
                "SD-100",
                Instant.now()
        );

        when(dispatcher.listLogs(eq(recipientId), eq("SMS"), any())).thenReturn(List.of(log1));

        mockMvc.perform(get("/api/v1/notifications/logs")
                        .param("recipientId", recipientId.toString())
                        .param("channel", "SMS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].logId").value(logId.toString()))
                .andExpect(jsonPath("$.data[0].channel").value("SMS"))
                .andExpect(jsonPath("$.data[0].status").value("SENT"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/logs/{logId} should return log by ID")
    void testGetLog() throws Exception {
        NotificationDispatchLogResponse logResp = new NotificationDispatchLogResponse(
                logId,
                recipientId,
                "EMAIL",
                "PARCEL_DELIVERED",
                "user@example.com",
                "Delivered",
                "Your parcel was delivered",
                "SENT",
                null,
                Instant.now(),
                "SD-200",
                Instant.now()
        );

        when(dispatcher.getLog(logId)).thenReturn(Optional.of(logResp));

        mockMvc.perform(get("/api/v1/notifications/logs/{logId}", logId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.logId").value(logId.toString()))
                .andExpect(jsonPath("$.data.channel").value("EMAIL"));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/logs/dispatch should trigger dispatch and return 201 Created")
    void testDispatchNotification() throws Exception {
        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId,
                "SMS",
                "PARCEL_PICKED_UP",
                "+94771234567",
                Map.of("trackingNumber", "SD-300"),
                "SD-300"
        );

        NotificationDispatchLogResponse logResp = new NotificationDispatchLogResponse(
                logId,
                recipientId,
                "SMS",
                "PARCEL_PICKED_UP",
                "+94771234567",
                "Picked Up",
                "Parcel SD-300 picked up",
                "SENT",
                null,
                Instant.now(),
                "SD-300",
                Instant.now()
        );

        when(dispatcher.dispatch(any(DispatchNotificationRequest.class))).thenReturn(logResp);

        mockMvc.perform(post("/api/v1/notifications/logs/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.logId").value(logId.toString()))
                .andExpect(jsonPath("$.data.status").value("SENT"));
    }
}
