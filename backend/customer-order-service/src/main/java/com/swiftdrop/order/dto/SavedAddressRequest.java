package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An address-book entry, in the same parts as the booking form. The rules match the order request
 * ({@link ValidationPatterns}), so a saved address always passes when it is used for a booking.
 */
@Schema(description = "A saved pickup or drop-off address")
public record SavedAddressRequest(
        @NotBlank @Size(min = 2, max = 40) @Pattern(regexp = ValidationPatterns.NAME_OR_LABEL, message = ValidationPatterns.NAME_MESSAGE)
        @Schema(description = "The customer's name for it", example = "Home")
        String label,

        @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = ValidationPatterns.NAME, message = ValidationPatterns.NAME_MESSAGE)
        @Schema(description = "Person at this address", example = "Nimal Perera")
        String contactName,

        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        @Schema(description = "Contact phone", example = "+94771234567")
        String contactPhone,

        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        @Schema(description = "Optional second phone", example = "+94112345678")
        String alternatePhone,

        @NotBlank @Size(max = 40) @Pattern(regexp = ValidationPatterns.ADDRESS_PART, message = ValidationPatterns.ADDRESS_MESSAGE)
        @Schema(description = "House or building number", example = "45/2")
        String house,

        @NotBlank @Size(min = 2, max = 150) @Pattern(regexp = ValidationPatterns.ADDRESS_PART, message = ValidationPatterns.ADDRESS_MESSAGE)
        @Schema(description = "Street or road", example = "Temple Road")
        String street,

        @Size(max = 98) @Pattern(regexp = ValidationPatterns.OPTIONAL_ADDRESS_PART, message = ValidationPatterns.ADDRESS_MESSAGE)
        @Schema(description = "Area or locality", example = "Nugegoda")
        String area,

        @Size(max = 100) @Pattern(regexp = ValidationPatterns.OPTIONAL_ADDRESS_PART, message = ValidationPatterns.ADDRESS_MESSAGE)
        @Schema(description = "Landmark", example = "Near the post office")
        String landmark,

        @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = ValidationPatterns.CITY, message = ValidationPatterns.CITY_MESSAGE)
        @Schema(description = "City or town", example = "Nugegoda")
        String city,

        @NotNull @Schema(description = "District")
        SriLankanDistrict district,

        @NotBlank @Pattern(regexp = ValidationPatterns.POSTAL_CODE, message = ValidationPatterns.POSTAL_CODE_MESSAGE)
        @Schema(description = "5-digit postal code", example = "10250")
        String postalCode
) {}
