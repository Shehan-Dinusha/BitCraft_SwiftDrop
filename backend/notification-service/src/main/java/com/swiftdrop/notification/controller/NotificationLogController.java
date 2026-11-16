package com.swiftdrop.notification.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.dto.NotificationDispatchLogResponse;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import com.swiftdrop.notification.util.SensitiveTemplates;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Dispatch audit logs and manual dispatch.
 *
 * <p>Access rules (caller identity comes from the gateway's verified {@code X-User-Id}/{@code X-User-Role}):
 * <ul>
 *   <li>DISPATCHER and SYSTEM_ADMIN may list or read any log and dispatch to any recipient.</li>
 *   <li>Every other role sees only its own logs and may only dispatch a test message to itself,
 *       always to its own resolved address (a request {@code destination} is ignored).</li>
 *   <li>One-time-code templates can never be dispatched here; they are sent only from the
 *       auth-iam-service OTP event.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/notifications/logs", "/api/v1/notification-log"})
@Tag(name = "NotificationLog", description = "Notification dispatch audit log and trigger APIs")
@RequiredArgsConstructor
public class NotificationLogController {

    static final UserRole[] OPERATIONS_ROLES = {UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN};

    private final MultiChannelNotificationDispatcher dispatcher;

    @GetMapping("/health")
    @Operation(summary = "Health check for notification log controller")
    public ApiResponse<String> health() {
        return ApiResponse.success("NotificationLogController is ready", "Health check OK");
    }

    @GetMapping
    @Operation(summary = "List notification dispatch audit logs",
            description = "DISPATCHER/SYSTEM_ADMIN: all logs, optionally filtered by recipientId. "
                    + "Other roles: only their own logs; asking for another recipient returns 403.")
    public ApiResponse<List<NotificationDispatchLogResponse>> listLogs(
            @RequestParam(required = false) UUID recipientId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            HttpServletRequest httpRequest) {

        CallerContext caller = CallerContext.from(httpRequest::getHeader);
        UUID targetRecipient;
        if (isOperations(caller)) {
            targetRecipient = recipientId; // null = every recipient (operations console)
        } else {
            if (recipientId != null) {
                caller.requireSelfOrRole(recipientId, OPERATIONS_ROLES);
            }
            targetRecipient = caller.userId();
        }

        List<NotificationDispatchLogResponse> logs = dispatcher.listLogs(targetRecipient, channel, status);
        return ApiResponse.success(logs, "Notification logs retrieved");
    }

    @GetMapping("/{logId}")
    @Operation(summary = "Get notification dispatch log detail by log UUID",
            description = "Owner of the log, DISPATCHER or SYSTEM_ADMIN.")
    public ApiResponse<NotificationDispatchLogResponse> getLog(
            @PathVariable @Parameter(description = "Log record UUID") UUID logId,
            HttpServletRequest httpRequest) {
        CallerContext caller = CallerContext.from(httpRequest::getHeader);
        return dispatcher.getLog(logId)
                .map(logResp -> {
                    caller.requireSelfOrRole(logResp.recipientId(), OPERATIONS_ROLES);
                    return ApiResponse.success(logResp, "Log retrieved");
                })
                .orElseGet(() -> ApiResponse.error("Notification log not found for ID: " + logId));
    }

    @PostMapping("/dispatch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ad-hoc / manual notification dispatch",
            description = "DISPATCHER/SYSTEM_ADMIN: any recipient. Other roles: only themselves, to their own "
                    + "address (destination is ignored). One-time-code templates are rejected with 403.")
    public ApiResponse<NotificationDispatchLogResponse> dispatchNotification(
            @Valid @RequestBody DispatchNotificationRequest request,
            HttpServletRequest httpRequest) {
        CallerContext caller = CallerContext.from(httpRequest::getHeader);

        if (SensitiveTemplates.isSensitive(request.templateCode())) {
            throw new ForbiddenException("One-time-code emails can only be requested by the auth service");
        }

        DispatchNotificationRequest effective = request;
        if (!isOperations(caller)) {
            caller.requireSelfOrRole(request.recipientId(), OPERATIONS_ROLES);
            // A self-service test must not become a relay to arbitrary addresses.
            effective = new DispatchNotificationRequest(request.recipientId(), request.channel(),
                    request.templateCode(), null, request.templateParams(), request.referenceId());
        }

        log.info("Manual dispatch by {} ({}) to recipient {}", caller.userId(), caller.role(), effective.recipientId());
        NotificationDispatchLogResponse response = dispatcher.dispatch(effective);
        return ApiResponse.success(response, "Notification dispatched");
    }

    private static boolean isOperations(CallerContext caller) {
        return caller.role() == UserRole.DISPATCHER || caller.role() == UserRole.SYSTEM_ADMIN;
    }
}
