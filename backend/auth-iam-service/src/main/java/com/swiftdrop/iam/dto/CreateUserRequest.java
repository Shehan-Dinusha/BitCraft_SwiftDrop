package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Administrator request to provision any platform account.")
public record CreateUserRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                message = "Username must be 3-50 characters and contain only letters, digits, dot, underscore, or hyphen")
        @Schema(description = "Unique username.") String username,

        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(description = "Unique email address.") String email,

        @Size(max = 32)
        @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Phone number is invalid")
        @Schema(description = "Optional contact phone number.") String phone,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "Given name.") String firstName,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "Family name.") String lastName,

        @NotBlank
        @Size(min = 8, max = 72)
        @Schema(description = "Initial password containing a letter and a digit.") String password,

        @NotNull
        @Schema(description = "Canonical application role.") UserRole role,

        @NotNull
        @Schema(description = "Primary operating district; mandatory for staff.") SriLankanDistrict district,

        @Null(message = "Merchant association is assigned only by the trusted approval workflow")
        @Schema(description = "Must be omitted or null. Association is assigned only by the trusted approval workflow.") UUID merchantId
) {
}
