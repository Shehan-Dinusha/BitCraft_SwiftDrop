package com.swiftdrop.notification.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.notification.dto.PushSubscriptionDtos.PushPublicKeyResponse;
import com.swiftdrop.notification.dto.PushSubscriptionDtos.PushSubscriptionRequest;
import com.swiftdrop.notification.dto.PushSubscriptionDtos.PushUnsubscribeRequest;
import com.swiftdrop.notification.service.push.DevicePushService;
import com.swiftdrop.notification.service.push.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Device (Web Push) notifications: the VAPID key and the caller's device subscriptions. */
@RestController
@Tag(name = "DevicePush", description = "Lock-screen notifications on the caller's devices (Web Push)")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final DevicePushService devicePushService;
    private final PushSubscriptionService subscriptionService;

    @GetMapping("/api/v1/notifications/push/public-key")
    @Operation(summary = "Get the VAPID public key browsers subscribe with",
            description = "enabled is false when the server has no VAPID keys; the in-app inbox still works then.")
    public ApiResponse<PushPublicKeyResponse> publicKey() {
        boolean enabled = devicePushService.enabled();
        return ApiResponse.success(new PushPublicKeyResponse(enabled, enabled ? devicePushService.publicKey() : null),
                enabled ? "Device notifications are available" : "Device notifications are not configured");
    }

    @PostMapping("/api/v1/notifications/me/push-subscriptions")
    @Operation(summary = "Register this device for the caller's notifications",
            description = "Upserts on the endpoint: a device that was registered by another account moves to the caller. "
                    + "Only https endpoints of the browsers' push services are accepted.")
    public ResponseEntity<ApiResponse<Void>> register(
            @RequestHeader(value = "X-User-Id", required = false)
            @Parameter(description = "Authenticated user UUID set by gateway", required = true) String headerUserId,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            @Valid @RequestBody PushSubscriptionRequest request) {
        UUID userId = parseUserId(headerUserId);
        if (userId == null) {
            return unauthorized();
        }
        if (!devicePushService.enabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Device notifications are not configured on this server"));
        }
        subscriptionService.register(userId, request.endpoint(), request.keys().p256dh(), request.keys().auth(), userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("This device will receive notifications"));
    }

    @DeleteMapping("/api/v1/notifications/me/push-subscriptions")
    @Operation(summary = "Stop notifications on this device",
            description = "Removes the device only if it belongs to the caller. Answers 200 either way, so sign-out can call it safely.")
    public ResponseEntity<ApiResponse<Boolean>> remove(
            @RequestHeader(value = "X-User-Id", required = false)
            @Parameter(description = "Authenticated user UUID set by gateway", required = true) String headerUserId,
            @Valid @RequestBody PushUnsubscribeRequest request) {
        UUID userId = parseUserId(headerUserId);
        if (userId == null) {
            return unauthorized();
        }
        boolean removed = subscriptionService.remove(userId, request.endpoint());
        return ResponseEntity.ok(ApiResponse.success(removed, removed ? "Device removed" : "Device was not registered"));
    }

    private static <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Missing or invalid X-User-Id authentication header"));
    }

    private static UUID parseUserId(String headerUserId) {
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
