package com.swiftdrop.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID logId = UUID.randomUUID();

    /** Simulates the verified identity headers the gateway adds after JWT validation. */
    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, UUID userId, String role) {
        return builder.header("X-User-Id", userId.toString()).header("X-User-Role", role);
    }

    private NotificationDispatchLogResponse logFor(UUID owner) {
        return new NotificationDispatchLogResponse(logId, owner, "EMAIL", "PARCEL_DELIVERED", "user@example.com",
                "Delivered", "Your parcel was delivered", "SENT", null, Instant.now(), "SD-200", Instant.now());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/logs/health should return 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/logs/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Requests without the gateway identity headers are rejected with 401")
    void testMissingIdentityIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/logs")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("A customer listing logs gets only their own")
    void testListLogs() throws Exception {
        when(dispatcher.listLogs(eq(recipientId), eq("EMAIL"), any())).thenReturn(List.of(logFor(recipientId)));

        mockMvc.perform(as(get("/api/v1/notifications/logs"), recipientId, "CUSTOMER")
                        .param("recipientId", recipientId.toString())
                        .param("channel", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].logId").value(logId.toString()))
                .andExpect(jsonPath("$.data[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.data[0].status").value("SENT"));
    }

    @Test
    @DisplayName("A customer asking for another user's logs gets 403")
    void testCustomerCannotListOtherUsersLogs() throws Exception {
        mockMvc.perform(as(get("/api/v1/notifications/logs"), recipientId, "CUSTOMER")
                        .param("recipientId", otherUserId.toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("Operations staff with no recipientId see every recipient's logs (console view)")
    void testAdminListsAllLogs() throws Exception {
        when(dispatcher.listLogs(isNull(), any(), any())).thenReturn(List.of(logFor(otherUserId)));

        mockMvc.perform(as(get("/api/v1/notifications/logs"), adminId, "SYSTEM_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].recipientId").value(otherUserId.toString()));

        verify(dispatcher).listLogs(isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("GET /logs/{logId} returns the caller's own log")
    void testGetLog() throws Exception {
        when(dispatcher.getLog(logId)).thenReturn(Optional.of(logFor(recipientId)));

        mockMvc.perform(as(get("/api/v1/notifications/logs/{logId}", logId), recipientId, "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.logId").value(logId.toString()))
                .andExpect(jsonPath("$.data.channel").value("EMAIL"));
    }

    @Test
    @DisplayName("GET /logs/{logId} for someone else's log returns 403; a dispatcher may read it")
    void testGetOtherUsersLog() throws Exception {
        when(dispatcher.getLog(logId)).thenReturn(Optional.of(logFor(otherUserId)));

        mockMvc.perform(as(get("/api/v1/notifications/logs/{logId}", logId), recipientId, "CUSTOMER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/v1/notifications/logs/{logId}", logId), adminId, "DISPATCHER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Operations staff can dispatch to any recipient, keeping the given destination")
    void testDispatchNotification() throws Exception {
        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId, "EMAIL", "PARCEL_PICKED_UP", "customer@example.com",
                Map.of("trackingNumber", "SD-300"), "SD-300");
        NotificationDispatchLogResponse logResp = new NotificationDispatchLogResponse(
                logId, recipientId, "EMAIL", "PARCEL_PICKED_UP", "customer@example.com", "Picked Up",
                "Parcel SD-300 picked up", "SENT", null, Instant.now(), "SD-300", Instant.now());
        when(dispatcher.dispatch(any(DispatchNotificationRequest.class))).thenReturn(logResp);

        mockMvc.perform(as(post("/api/v1/notifications/logs/dispatch"), adminId, "DISPATCHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.logId").value(logId.toString()))
                .andExpect(jsonPath("$.data.status").value("SENT"));

        ArgumentCaptor<DispatchNotificationRequest> sent = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        verify(dispatcher).dispatch(sent.capture());
        assertThat(sent.getValue().destination()).isEqualTo("customer@example.com");
    }

    @Test
    @DisplayName("A customer test dispatch to themselves ignores the requested destination (no relay)")
    void testCustomerSelfDispatchIgnoresDestination() throws Exception {
        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId, "EMAIL", "PARCEL_OUT_FOR_DELIVERY", "victim@example.com", Map.of(), "TEST");
        when(dispatcher.dispatch(any(DispatchNotificationRequest.class))).thenReturn(logFor(recipientId));

        mockMvc.perform(as(post("/api/v1/notifications/logs/dispatch"), recipientId, "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        ArgumentCaptor<DispatchNotificationRequest> sent = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        verify(dispatcher).dispatch(sent.capture());
        assertThat(sent.getValue().recipientId()).isEqualTo(recipientId);
        assertThat(sent.getValue().destination()).isNull();
    }

    @Test
    @DisplayName("A customer cannot dispatch to another user")
    void testCustomerCannotDispatchToOthers() throws Exception {
        DispatchNotificationRequest request = new DispatchNotificationRequest(
                otherUserId, "EMAIL", "PARCEL_PICKED_UP", null, Map.of(), "SD-1");

        mockMvc.perform(as(post("/api/v1/notifications/logs/dispatch"), recipientId, "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("Nobody can send a one-time-code template through manual dispatch, not even an admin")
    void testOtpTemplatesCannotBeDispatchedManually() throws Exception {
        DispatchNotificationRequest request = new DispatchNotificationRequest(
                otherUserId, "EMAIL", "PASSWORD_RESET", "victim@example.com",
                Map.of("code", "123456", "expiresInMinutes", "10"), null);

        mockMvc.perform(as(post("/api/v1/notifications/logs/dispatch"), adminId, "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/logs/dispatch rejects the removed SMS channel with 400")
    void testDispatchRejectsSmsChannel() throws Exception {
        DispatchNotificationRequest request = new DispatchNotificationRequest(
                recipientId, "SMS", "PARCEL_PICKED_UP", "+94771234567", Map.of(), "SD-301");

        mockMvc.perform(as(post("/api/v1/notifications/logs/dispatch"), adminId, "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dispatcher);
    }
}
