package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Self-service account registration request. Staff roles are provisioned by an administrator.")
public record RegisterUserRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                message = "Username must be 3-50 characters and contain only letters, digits, dot, underscore, or hyphen")
        @Schema(description = "Unique username.", example = "customer.one")
        String username,

        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(description = "Unique email address.", example = "customer.one@example.com")
        String email,

        @Size(max = 32)
        @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Phone number is invalid")
        @Schema(description = "Optional E.164-compatible phone number.", example = "+94771234567")
        String phone,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "Given name.", example = "Nimal")
        String firstName,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "Family name.", example = "Perera")
        String lastName,

        @NotBlank
        @Size(min = 8, max = 72)
        @Schema(description = "Password containing at least one letter and one digit.", example = "SwiftDrop123")
        String password,

        @NotNull
        @Schema(description = "Self-service roles are CUSTOMER and MERCHANT.", example = "CUSTOMER")
        UserRole role,

        @NotNull
        @Schema(description = "Primary operating district.", example = "COLOMBO")
        SriLankanDistrict district,

        @Schema(description = "Merchant identifier required for MERCHANT accounts.", example = "0f5e8c4a-2c6d-4a58-9c8f-4c2c4e7b8a11")
        UUID merchantId
) {
}
