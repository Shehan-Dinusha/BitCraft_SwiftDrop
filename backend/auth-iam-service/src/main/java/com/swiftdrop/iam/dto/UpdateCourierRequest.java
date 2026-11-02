package com.swiftdrop.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Replace courier contact details. Role, district and dispatch data cannot be changed here.")
public record UpdateCourierRequest(
        @NotBlank @Email @Size(max = 320)
        @Schema(description = "Unique email address. Changing it revokes existing sessions.") String email,
        @Size(max = 32) @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$")
        @Schema(description = "Optional contact phone number; omit to clear.") String phone,
        @NotBlank @Size(max = 100)
        @Schema(description = "Given name.") String firstName,
        @NotBlank @Size(max = 100)
        @Schema(description = "Family name.") String lastName
) {
}
