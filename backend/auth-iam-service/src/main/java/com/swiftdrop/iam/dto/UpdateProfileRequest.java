package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The details a signed-in user may change about themselves. Username (the sign-in identifier),
 * email (needs re-verification), role and status are not part of it.
 */
@Schema(description = "Self-service profile update")
public record UpdateProfileRequest(
        @NotBlank
        @Size(max = 100)
        @Schema(description = "Given name.", example = "Nimal")
        String firstName,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "Family name.", example = "Perera")
        String lastName,

        @Size(max = 32)
        @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Phone number is invalid")
        @Schema(description = "Phone number; empty removes it.", example = "+94771234567")
        String phone,

        @NotNull
        @Schema(description = "Primary operating district.", example = "COLOMBO")
        SriLankanDistrict district
) {
}
