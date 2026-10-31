package com.swiftdrop.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

@Schema(description = "Update omnichannel notification preferences request")
public record UpdatePreferenceRequest(
        @Schema(description = "Enable or disable email channel")
        Boolean emailEnabled,

        @Schema(description = "Enable or disable SMS channel")
        Boolean smsEnabled,

        @Schema(description = "Enable or disable in-app push channel")
        Boolean pushEnabled,

        @Email(message = "Email address must be well-formed")
        @Schema(description = "Updated email destination", example = "recipient@example.com")
        String emailAddress,

        @Schema(description = "Updated mobile phone destination (E.164)", example = "+94771234567")
        String phoneNumber,

        @Schema(description = "Language preference (e.g. en, si, ta)", example = "en")
        String languagePreference
) {
}
