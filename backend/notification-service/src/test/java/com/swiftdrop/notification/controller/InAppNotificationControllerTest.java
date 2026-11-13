package com.swiftdrop.notification.controller;

import com.swiftdrop.notification.domain.entity.InAppNotification;
import com.swiftdrop.notification.service.InAppNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InAppNotificationController.class)
class InAppNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InAppNotificationService notificationService;

    @Test
    @DisplayName("GET /api/v1/notifications/me should return notifications scoped to X-User-Id")
    void testListMyNotifications_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        InAppNotification n1 = InAppNotification.builder()
                .id(1L)
                .userId(userId)
                .title("Out for Delivery")
                .body("Parcel SD-001 is out for delivery")
                .trackingNumber("SD-001")
                .read(false)
                .createdAt(Instant.now())
                .build();

        when(notificationService.listNotifications(userId)).thenReturn(List.of(n1));

        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Out for Delivery"))
                .andExpect(jsonPath("$.data[0].trackingNumber").value("SD-001"))
                .andExpect(jsonPath("$.data[0].read").value(false));

        verify(notificationService).listNotifications(userId);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/me should return 401 when X-User-Id is missing or invalid")
    void testListMyNotifications_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing or invalid X-User-Id authentication header"));

        mockMvc.perform(get("/api/v1/notifications/me")
                        .header("X-User-Id", "not-a-valid-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).listNotifications(any());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/me/unread-count should return unread count for user")
    void testGetUnreadCount_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationService.getUnreadCount(userId)).thenReturn(4L);

        mockMvc.perform(get("/api/v1/notifications/me/unread-count")
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(4));

        verify(notificationService).getUnreadCount(userId);
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/me/{id}/read should mark notification read when owned by user")
    void testMarkAsRead_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        Long notificationId = 10L;
        InAppNotification updated = InAppNotification.builder()
                .id(notificationId)
                .userId(userId)
                .title("Delivered")
                .body("Parcel delivered")
                .trackingNumber("SD-777")
                .read(true)
                .createdAt(Instant.now())
                .build();

        when(notificationService.markAsRead(notificationId, userId)).thenReturn(Optional.of(updated));

        mockMvc.perform(patch("/api/v1/notifications/me/{id}/read", notificationId)
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.read").value(true));

        verify(notificationService).markAsRead(notificationId, userId);
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/me/{id}/read should return 404 when notification not found or owned by another user")
    void testMarkAsRead_NotFoundOrForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        Long notificationId = 99L;
        when(notificationService.markAsRead(notificationId, userId)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/notifications/me/{id}/read", notificationId)
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        verify(notificationService).markAsRead(notificationId, userId);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/me/read-all should mark all user alerts as read")
    void testMarkAllAsRead_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationService.markAllAsRead(userId)).thenReturn(7);

        mockMvc.perform(post("/api/v1/notifications/me/read-all")
                        .header("X-User-Id", userId.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(7));

        verify(notificationService).markAllAsRead(userId);
    }
}
