package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Provision a courier identity. Dispatchers may use only their own district.")
public record CreateCourierRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$")
        @Schema(description = "Unique username.") String username,
        @NotBlank @Email @Size(max = 320)
        @Schema(description = "Unique email address.") String email,
        @Size(max = 32) @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$")
        @Schema(description = "Optional contact phone number.") String phone,
        @NotBlank @Size(max = 100)
        @Schema(description = "Given name.") String firstName,
        @NotBlank @Size(max = 100)
        @Schema(description = "Family name.") String lastName,
        @NotBlank @Size(min = 8, max = 72)
        @Schema(description = "Initial password containing a letter and a digit.") String password,
        @NotNull @Schema(description = "Courier operating district.") SriLankanDistrict district
) {
}
