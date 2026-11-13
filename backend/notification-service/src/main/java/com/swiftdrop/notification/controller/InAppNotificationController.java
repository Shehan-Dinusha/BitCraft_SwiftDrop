package com.swiftdrop.notification.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.notification.domain.entity.InAppNotification;
import com.swiftdrop.notification.dto.InAppNotificationResponse;
import com.swiftdrop.notification.service.InAppNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/me")
@Tag(name = "InAppNotification", description = "In-app notifications for authenticated users")
@RequiredArgsConstructor
public class InAppNotificationController {

    private final InAppNotificationService notificationService;

    @GetMapping
    @Operation(summary = "List all in-app notifications for the authenticated user")
    public ResponseEntity<ApiResponse<List<InAppNotificationResponse>>> listMyNotifications(
            @RequestHeader(value = "X-User-Id", required = false)
            @Parameter(description = "Authenticated user UUID set by gateway", required = true)
            String headerUserId) {

        UUID userId = parseUserId(headerUserId);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing or invalid X-User-Id authentication header"));
        }

        List<InAppNotification> entities = notificationService.listNotifications(userId);
        List<InAppNotificationResponse> responses = entities.stream()
                .map(this::mapToResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses, "In-app notifications retrieved"));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread in-app notification count for the authenticated user")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false)
            @Parameter(description = "Authenticated user UUID set by gateway", required = true)
            String headerUserId) {

        UUID userId = parseUserId(headerUserId);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing or invalid X-User-Id authentication header"));
        }

        long unreadCount = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(unreadCount, "Unread count retrieved"));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a single in-app notification as read")
    public ResponseEntity<ApiResponse<InAppNotificationResponse>> markAsRead(
            @PathVariable @Parameter(description = "Notification ID") Long id,
            @RequestHeader(value = "X-User-Id", required = false)
            @Parameter(description = "Authenticated user UUID set by gateway", required = true)
            String headerUserId) {

        UUID userId = parseUserId(headerUserId);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing or invalid X-User-Id authentication header"));
        }

        Optional<InAppNotification> updated = notificationService.markAsRead(id, userId);
        return updated.map(inAppNotification -> ResponseEntity.ok(ApiResponse.success(
                        mapToResponse(inAppNotification),
                        "Notification marked as read"
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Notification not found for ID: " + id)));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all unread in-app notifications as read for the authenticated user")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(
            @RequestHeader(value = "X-User-Id", required = false)
            @Parameter(description = "Authenticated user UUID set by gateway", required = true)
            String headerUserId) {

        UUID userId = parseUserId(headerUserId);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing or invalid X-User-Id authentication header"));
        }

        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(count, "Marked " + count + " notifications as read"));
    }

    private InAppNotificationResponse mapToResponse(InAppNotification entity) {
        return new InAppNotificationResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getTitle(),
                entity.getBody(),
                entity.getTrackingNumber(),
                entity.isRead(),
                entity.getCreatedAt()
        );
    }

    private UUID parseUserId(String headerUserId) {
        if (headerUserId == null || headerUserId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(headerUserId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
