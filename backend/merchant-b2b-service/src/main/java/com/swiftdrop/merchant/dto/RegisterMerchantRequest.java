package com.swiftdrop.merchant.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Business application; owner identity comes from the authenticated gateway")
public record RegisterMerchantRequest(
        @NotBlank @Size(max = 150) @Schema(description = "Business name") String businessName,
        @NotBlank @Size(max = 64) @Schema(description = "Business registration number") String businessRegNo,
        @NotBlank @Email @Size(max = 100) @Schema(description = "Business email") String contactEmail,
        @NotBlank @Size(max = 30) @Schema(description = "Business phone") String contactPhone,
        @NotBlank @Size(max = 255) @Schema(description = "Pickup address") String pickupAddress,
        @NotNull @Schema(description = "Operating district") SriLankanDistrict operatingDistrict
) {}
