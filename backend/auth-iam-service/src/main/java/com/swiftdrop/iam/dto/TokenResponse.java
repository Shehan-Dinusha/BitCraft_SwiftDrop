package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Issued access and refresh token pair.")
public record TokenResponse(
        @Schema(description = "Signed JWT access token.") String accessToken,
        @Schema(description = "Opaque refresh token. Store securely and never log it.") String refreshToken,
        @Schema(description = "Token type.", example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds.", example = "3600") long expiresIn,
        @Schema(description = "Authenticated user public identifier.") String userId,
        @Schema(description = "Username.") String username,
        @Schema(description = "Email address.") String email,
        @Schema(description = "Primary role.") UserRole role,
        @Schema(description = "Primary district.") SriLankanDistrict district
) {
}
