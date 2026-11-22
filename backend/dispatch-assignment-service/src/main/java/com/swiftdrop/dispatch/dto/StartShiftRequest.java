package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.phone.SriLankanPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

@Schema(description = "Request to start or register a courier shift. The vehicle type and parcel capacity come "
        + "from the courier's registered vehicle (PUT /api/v1/dispatch/couriers/me/profile) and the district from "
        + "their account, so a courier only works in their home district.")
public record StartShiftRequest(
        @NotNull(message = "Courier ID is required")
        @Schema(description = "Courier UUID")
        UUID courierId,

        @NotBlank(message = "Courier name is required")
        @Schema(description = "Full name of the courier", example = "Sunil Perera")
        String courierName,

        @Pattern(regexp = SriLankanPhone.OPTIONAL_REGEX, message = SriLankanPhone.MESSAGE)
        @Schema(description = "Contact phone number", example = "+94771234567")
        String phone
) {}
