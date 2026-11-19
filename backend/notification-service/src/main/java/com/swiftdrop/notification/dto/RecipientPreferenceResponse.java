package com.swiftdrop.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Recipient omnichannel notification preferences")
public record RecipientPreferenceResponse(
        @Schema(description = "Recipient UUID")
        UUID recipientId,

        @Schema(description = "Recipient persona role", example = "CUSTOMER")
        String recipientType,

        @Schema(description = "Whether email notifications are enabled")
        boolean emailEnabled,


        @Schema(description = "Whether in-app push notifications are enabled")
        boolean pushEnabled,

        @Schema(description = "Recipient contact email", example = "customer@example.com")
        String emailAddress,


        @Schema(description = "Preferred language code", example = "en")
        String languagePreference,

        @Schema(description = "Last preference update timestamp")
        Instant updatedAt
) {
}
