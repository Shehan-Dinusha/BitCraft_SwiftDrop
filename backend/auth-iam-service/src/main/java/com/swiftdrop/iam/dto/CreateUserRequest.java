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

@Schema(description = "Administrator request to provision any platform account.")
public record CreateUserRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                message = "Username must be 3-50 characters and contain only letters, digits, dot, underscore, or hyphen")
        String username,

        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        @Size(max = 32)
        @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Phone number is invalid")
        String phone,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotNull
        UserRole role,

        @NotNull
        SriLankanDistrict district,

        UUID merchantId
) {
}
