package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.phone.SriLankanPhone;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Registers a courier. No password is set: the courier receives an email invite "
        + "and chooses their own.")
public record CreateCourierRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                message = "Username must be 3-50 characters and contain only letters, digits, dot, underscore, or hyphen")
        @Schema(example = "kasun.colombo")
        String username,

        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(example = "kasun@example.com")
        String email,

        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = SriLankanPhone.REGEX, message = SriLankanPhone.MESSAGE)
        @Schema(example = "+94771234567")
        String phone,

        @NotBlank
        @Size(max = 100)
        @Schema(example = "Kasun")
        String firstName,

        @NotBlank
        @Size(max = 100)
        @Schema(example = "Perera")
        String lastName,

        @Schema(description = "Only used when a SYSTEM_ADMIN registers the courier. A dispatcher always "
                + "registers couriers in their own district and this field is ignored.", example = "COLOMBO")
        SriLankanDistrict district
) {
}
