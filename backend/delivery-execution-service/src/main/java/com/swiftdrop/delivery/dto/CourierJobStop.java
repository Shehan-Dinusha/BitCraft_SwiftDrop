package com.swiftdrop.delivery.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "A contact and address the courier visits.")
public record CourierJobStop(
        @Schema(description = "Contact name", example = "Nimal Perera") String name,
        @Schema(description = "Contact phone", example = "+94771234567") String phone,
        @Schema(description = "Alternate phone", example = "+94712345678") String alternatePhone,
        @Schema(description = "Address line 1", example = "12 Galle Road") String addressLine1,
        @Schema(description = "Address line 2", example = "Second floor") String addressLine2,
        @Schema(description = "City", example = "Colombo 03") String city,
        @Schema(description = "Postal code", example = "00300") String postalCode,
        @Schema(description = "District", example = "COLOMBO") SriLankanDistrict district
) {
}
