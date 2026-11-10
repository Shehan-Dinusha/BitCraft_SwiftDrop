package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/oauth2/exchange}.
 * Swaps a one-time server-issued code for a full token pair.
 * The code is valid for 2 minutes and is single-use.
 */
@Schema(description = "One-time code returned from the Google OAuth callback, to be exchanged for tokens")
public record OAuthExchangeRequest(

        @NotBlank(message = "code is required")
        @Schema(description = "One-time exchange code from the OAuth callback redirect", example = "abc123...")
        String code
) {
}

