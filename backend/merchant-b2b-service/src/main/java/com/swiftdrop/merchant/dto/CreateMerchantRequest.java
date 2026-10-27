package com.swiftdrop.merchant.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Merchant profile creation request")
public record CreateMerchantRequest(
        @NotBlank(message = "Business name is required")
        @Schema(description = "Business or corporate name", example = "Colombo Retail Hub Ltd")
        String businessName,

        @NotBlank(message = "Contact email is required")
        @Email(message = "Valid email is required")
        @Schema(description = "Business contact email", example = "orders@colomboretail.lk")
        String contactEmail,

        @NotBlank(message = "Contact phone is required")
        @Schema(description = "Contact phone", example = "+94 11 234 5678")
        String contactPhone,

        @NotBlank(message = "Pickup address is required")
        @Schema(description = "Pickup warehouse or store address", example = "104 Galle Road, Colombo 03")
        String pickupAddress,

        @NotNull(message = "Operating district is required")
        @Schema(description = "Operating district", example = "COLOMBO")
        SriLankanDistrict operatingDistrict
) {
}
