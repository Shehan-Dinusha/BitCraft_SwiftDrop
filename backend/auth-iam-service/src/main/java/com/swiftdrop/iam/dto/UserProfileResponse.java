package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Safe public representation of a platform account.")
public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        String phone,
        String firstName,
        String lastName,
        UserRole role,
        SriLankanDistrict district,
        UUID merchantId,
        UserStatus status,
        @Schema(description = "True once the email address has been verified.") boolean emailVerified,
        Instant lastLoginAt,
        Instant createdAt
) {
}
