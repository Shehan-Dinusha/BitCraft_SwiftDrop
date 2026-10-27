package com.swiftdrop.notification.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.notification.dto.RecipientPreferenceResponse;
import com.swiftdrop.notification.dto.UpdatePreferenceRequest;
import com.swiftdrop.notification.service.RecipientPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/notifications/preferences", "/api/v1/preference"})
@Tag(name = "Preference", description = "Recipient notification preferences management")
@RequiredArgsConstructor
public class PreferenceController {

    private final RecipientPreferenceService preferenceService;

    @GetMapping("/health")
    @Operation(summary = "Health check for preference controller")
    public ApiResponse<String> health() {
        return ApiResponse.success("PreferenceController is ready", "Health check OK");
    }

    @GetMapping("/{recipientId}")
    @Operation(summary = "Get notification preferences for a recipient")
    public ApiResponse<RecipientPreferenceResponse> getPreferences(
            @PathVariable @Parameter(description = "Recipient UUID") UUID recipientId) {
        RecipientPreferenceResponse response = preferenceService.getOrCreatePreferences(recipientId);
        return ApiResponse.success(response, "Preferences retrieved");
    }

    @GetMapping
    @Operation(summary = "Get current authenticated user notification preferences")
    public ApiResponse<RecipientPreferenceResponse> getCurrentUserPreferences(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(required = false) UUID recipientId) {

        UUID targetRecipient = recipientId;
        if (targetRecipient == null && headerUserId != null && !headerUserId.isBlank()) {
            try {
                targetRecipient = UUID.fromString(headerUserId);
            } catch (IllegalArgumentException ignored) {}
        }
        if (targetRecipient == null) {
            targetRecipient = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }

        RecipientPreferenceResponse response = preferenceService.getOrCreatePreferences(targetRecipient);
        return ApiResponse.success(response, "Preferences retrieved");
    }

    @PutMapping("/{recipientId}")
    @Operation(summary = "Update notification preferences for a recipient")
    public ApiResponse<RecipientPreferenceResponse> updatePreferences(
            @PathVariable UUID recipientId,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        RecipientPreferenceResponse response = preferenceService.updatePreferences(recipientId, request);
        return ApiResponse.success(response, "Notification preferences updated successfully");
    }
}
