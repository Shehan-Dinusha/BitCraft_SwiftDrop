package com.swiftdrop.notification.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/notifications/logs", "/api/v1/notification-log"})
@Tag(name = "NotificationLog", description = "Notification dispatch audit log and trigger APIs")
@RequiredArgsConstructor
public class NotificationLogController {

    private final MultiChannelNotificationDispatcher dispatcher;

    @GetMapping("/health")
    @Operation(summary = "Health check for notification log controller")
    public ApiResponse<String> health() {
        return ApiResponse.success("NotificationLogController is ready", "Health check OK");
    }

    @GetMapping
    @Operation(summary = "List notification dispatch audit logs")
    public ApiResponse<List<NotificationDispatchLogResponse>> listLogs(
            @RequestParam(required = false) UUID recipientId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {

        UUID targetRecipient = recipientId;
        if (targetRecipient == null && headerUserId != null && !headerUserId.isBlank()) {
            try {
                targetRecipient = UUID.fromString(headerUserId);
            } catch (IllegalArgumentException ignored) {}
        }

        List<NotificationDispatchLogResponse> logs = dispatcher.listLogs(targetRecipient, channel, status);
        return ApiResponse.success(logs, "Notification logs retrieved");
    }

    @GetMapping("/{logId}")
    @Operation(summary = "Get notification dispatch log detail by log UUID")
    public ApiResponse<NotificationDispatchLogResponse> getLog(
            @PathVariable @Parameter(description = "Log record UUID") UUID logId) {
        return dispatcher.getLog(logId)
                .map(logResp -> ApiResponse.success(logResp, "Log retrieved"))
                .orElseGet(() -> ApiResponse.error("Notification log not found for ID: " + logId));
    }

    @PostMapping("/dispatch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ad-hoc / manual notification dispatch")
    public ApiResponse<NotificationDispatchLogResponse> dispatchNotification(
            @Valid @RequestBody DispatchNotificationRequest request) {
        log.info("Request received to dispatch notification to recipient {}", request.recipientId());
        NotificationDispatchLogResponse response = dispatcher.dispatch(request);
        return ApiResponse.success(response, "Notification dispatched");
    }
}
