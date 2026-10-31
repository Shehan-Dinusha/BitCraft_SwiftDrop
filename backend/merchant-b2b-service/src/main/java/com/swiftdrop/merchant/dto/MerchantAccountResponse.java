package com.swiftdrop.merchant.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Merchant profile response")
public record MerchantAccountResponse(
        @Schema(description = "Merchant unique UUID identifier")
        UUID merchantId,

        @Schema(description = "Business or registered corporate name")
        String businessName,

        @Schema(description = "Primary business contact email")
        String contactEmail,

        @Schema(description = "Contact telephone number")
        String contactPhone,

        @Schema(description = "Default pickup location / warehouse address")
        String pickupAddress,

        @Schema(description = "Primary operating Sri Lankan district")
        SriLankanDistrict operatingDistrict,

        @Schema(description = "Account operational status")
        String status,

        @Schema(description = "Registration timestamp")
        Instant createdAt
) {
}
