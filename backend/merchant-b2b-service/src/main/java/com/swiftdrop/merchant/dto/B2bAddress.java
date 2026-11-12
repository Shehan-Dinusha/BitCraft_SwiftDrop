package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Pickup or delivery address and contact")
public record B2bAddress(
        @NotBlank @Size(min = 5, max = 200) @Schema(description = "Street address") String addressLine1,
        @NotBlank @Size(min = 2, max = 100) @Schema(description = "City") String city,
        @NotBlank @Schema(description = "Sri Lankan district name; spaces and hyphens accepted") String district,
        @NotBlank @Pattern(regexp = "\\d{5}") @Schema(description = "Five-digit postal code") String postalCode,
        @NotBlank @Size(min = 2, max = 100) @Schema(description = "Contact name") String contactName,
        @NotBlank @Schema(description = "Sri Lankan local or international phone") String contactPhone
) {}
