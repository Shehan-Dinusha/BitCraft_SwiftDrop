package com.swiftdrop.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;
import java.util.UUID;

@Schema(description = "Manual / Ad-hoc notification dispatch request")
public record DispatchNotificationRequest(
        @NotNull(message = "Recipient UUID is required")
        @Schema(description = "Recipient UUID")
        UUID recipientId,

        @NotBlank(message = "Channel is required (EMAIL or PUSH)")
        @Pattern(regexp = "(?i)EMAIL|PUSH", message = "Channel must be EMAIL or PUSH")
        @Schema(description = "Delivery channel", example = "EMAIL", allowableValues = {"EMAIL", "PUSH"})
        String channel,

        @NotBlank(message = "Template code is required")
        @Schema(description = "Template code identifier", example = "PARCEL_OUT_FOR_DELIVERY")
        String templateCode,

        @Schema(description = "Override destination email address (optional)")
        String destination,

        @Schema(description = "Template replacement parameter key-value pairs")
        Map<String, String> templateParams,

        @Schema(description = "Associated order ID or tracking number", example = "SD-2026-COL-001")
        String referenceId
) {
}
