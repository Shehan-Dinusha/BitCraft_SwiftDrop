package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh-token rotation request.")
public record RefreshTokenRequest(
        @NotBlank
        @Schema(description = "Opaque refresh token from the previous token response.")
        String refreshToken
) {
}
