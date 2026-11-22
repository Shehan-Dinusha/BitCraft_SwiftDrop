package com.swiftdrop.iam.dto;

import com.swiftdrop.contracts.phone.SriLankanPhone;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
        @Pattern(regexp = SriLankanPhone.OPTIONAL_REGEX, message = SriLankanPhone.MESSAGE)
        @Schema(description = "Phone number; empty removes it.", example = "+94771234567")
        String phone,

        @Schema(description = "District. Couriers and dispatchers must send their assigned district unchanged; "
                + "customers and merchants may leave it out.", example = "COLOMBO")
        SriLankanDistrict district
) {
}
