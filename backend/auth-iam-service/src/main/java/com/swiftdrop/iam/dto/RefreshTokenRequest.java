package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Refresh-token rotation request. Browsers send no body: the token travels in the "
        + "HttpOnly swiftdrop_refresh cookie.")
public record RefreshTokenRequest(
        @Schema(description = "Opaque refresh token. Optional when the swiftdrop_refresh cookie is sent.")
        String refreshToken
) {
}
