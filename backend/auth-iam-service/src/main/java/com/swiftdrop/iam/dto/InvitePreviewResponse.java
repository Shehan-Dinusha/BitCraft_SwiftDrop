package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Who a valid staff invite is for, shown on the invite page before the password is chosen.")
public record InvitePreviewResponse(
        @Schema(description = "First name of the invited person.", example = "Kasun")
        String firstName,
        @Schema(description = "Username the person will sign in with.", example = "kasun.colombo")
        String username,
        @Schema(description = "Role of the invited account.", example = "COURIER")
        UserRole role,
        @Schema(description = "District the account works in.", example = "COLOMBO")
        SriLankanDistrict district,
        @Schema(description = "When the invite link expires.")
        Instant expiresAt
) {
}
