package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Accepts a staff invite by choosing the account's password.")
public record AcceptInviteRequest(
        @NotBlank
        @Size(max = 64)
        @Schema(description = "Invite token from the emailed link.")
        String token,

        @NotBlank
        @Size(min = 8, max = 72)
        @Schema(description = "New password: 8-72 characters with at least one letter and one digit.", example = "SwiftDrop123")
        String password
) {
}
