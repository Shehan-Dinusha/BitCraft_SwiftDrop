package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Sets a new password using the 6-digit code that was emailed to the user.")
public record ResetPasswordRequest(
        @NotBlank
        @Size(max = 320)
        @Schema(description = "Username or email address of the account.", example = "customer.one@example.com")
        String identifier,

        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "Code must be 6 digits")
        @Schema(description = "6-digit reset code.", example = "482913")
        String code,

        @NotBlank
        @Size(min = 8, max = 72)
        @Schema(description = "New password: 8-72 characters with at least one letter and one digit.", example = "NewSwiftDrop123")
        String newPassword
) {
}
