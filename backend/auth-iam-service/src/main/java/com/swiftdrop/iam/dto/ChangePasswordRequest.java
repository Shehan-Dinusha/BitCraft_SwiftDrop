package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Authenticated password change request.")
public record ChangePasswordRequest(
        @NotBlank
        @Size(max = 72)
        @Schema(description = "Current password.")
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 72)
        @Schema(description = "New password containing at least one letter and one digit.")
        String newPassword
) {
}
