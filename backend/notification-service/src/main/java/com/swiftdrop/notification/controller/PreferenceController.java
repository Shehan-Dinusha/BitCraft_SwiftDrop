package com.swiftdrop.notification.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.notification.dto.RecipientPreferenceResponse;
import com.swiftdrop.notification.dto.UpdatePreferenceRequest;
import com.swiftdrop.notification.service.RecipientPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Notification preferences. Callers read and change only their own preferences;
 * SYSTEM_ADMIN may manage anyone's, DISPATCHER may read them. Identity comes from the
 * gateway's verified {@code X-User-Id}/{@code X-User-Role} headers.
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/notifications/preferences", "/api/v1/preference"})
@Tag(name = "Preference", description = "Recipient notification preferences management")
@RequiredArgsConstructor
public class PreferenceController {

    private static final UserRole[] READ_ANY = {UserRole.DISPATCHER, UserRole.SYSTEM_ADMIN};
    private static final UserRole[] UPDATE_ANY = {UserRole.SYSTEM_ADMIN};

    private final RecipientPreferenceService preferenceService;

    @GetMapping("/health")
    @Operation(summary = "Health check for preference controller")
    public ApiResponse<String> health() {
        return ApiResponse.success("PreferenceController is ready", "Health check OK");
    }

    @GetMapping("/{recipientId}")
    @Operation(summary = "Get notification preferences for a recipient",
            description = "The recipient themselves, DISPATCHER or SYSTEM_ADMIN; otherwise 403.")
    public ApiResponse<RecipientPreferenceResponse> getPreferences(
            @PathVariable @Parameter(description = "Recipient UUID") UUID recipientId,
            HttpServletRequest httpRequest) {
        CallerContext.from(httpRequest::getHeader).requireSelfOrRole(recipientId, READ_ANY);
        RecipientPreferenceResponse response = preferenceService.getOrCreatePreferences(recipientId);
        return ApiResponse.success(response, "Preferences retrieved");
    }

    @GetMapping
    @Operation(summary = "Get current authenticated user notification preferences",
            description = "Returns the caller's own preferences. recipientId is only honoured for the caller "
                    + "themselves, DISPATCHER or SYSTEM_ADMIN.")
    public ApiResponse<RecipientPreferenceResponse> getCurrentUserPreferences(
            @RequestParam(required = false) UUID recipientId,
            HttpServletRequest httpRequest) {
        CallerContext caller = CallerContext.from(httpRequest::getHeader);
        UUID targetRecipient = recipientId == null ? caller.userId() : recipientId;
        caller.requireSelfOrRole(targetRecipient, READ_ANY);

        RecipientPreferenceResponse response = preferenceService.getOrCreatePreferences(targetRecipient);
        return ApiResponse.success(response, "Preferences retrieved");
    }

    @PutMapping("/{recipientId}")
    @Operation(summary = "Update notification preferences for a recipient",
            description = "The recipient themselves or SYSTEM_ADMIN; otherwise 403.")
    public ApiResponse<RecipientPreferenceResponse> updatePreferences(
            @PathVariable UUID recipientId,
            @Valid @RequestBody UpdatePreferenceRequest request,
            HttpServletRequest httpRequest) {
        CallerContext.from(httpRequest::getHeader).requireSelfOrRole(recipientId, UPDATE_ANY);
        RecipientPreferenceResponse response = preferenceService.updatePreferences(recipientId, request);
        return ApiResponse.success(response, "Notification preferences updated successfully");
    }
}
