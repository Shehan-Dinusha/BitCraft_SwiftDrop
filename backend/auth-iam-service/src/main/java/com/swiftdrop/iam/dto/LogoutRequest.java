package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Optional logout request. Supplying the refresh token revokes that session.")
public record LogoutRequest(
        @Schema(description = "Opaque refresh token to revoke. The access token is taken from the Authorization header.")
        String refreshToken
) {
}
