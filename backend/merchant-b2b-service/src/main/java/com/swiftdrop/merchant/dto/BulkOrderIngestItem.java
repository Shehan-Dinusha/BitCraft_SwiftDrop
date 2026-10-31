package com.swiftdrop.merchant.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Single order item within a bulk order ingestion batch")
public record BulkOrderIngestItem(
        @NotBlank(message = "Recipient name is required")
        @Schema(description = "Recipient name", example = "Amara Gunawardena")
        String recipientName,

        @NotBlank(message = "Recipient phone is required")
        @Schema(description = "Recipient phone number", example = "+94 77 987 6543")
        String recipientPhone,

        @NotNull(message = "Delivery district is required")
        @Schema(description = "Delivery destination district", example = "GAMPAHA")
        SriLankanDistrict deliveryDistrict,

        @NotBlank(message = "Delivery address is required")
        @Schema(description = "Full delivery address", example = "45 Kandy Road, Kadawatha")
        String deliveryAddress,

        @DecimalMin(value = "0.1", message = "Package weight must be at least 0.1 kg")
        @Schema(description = "Package weight in kilograms", example = "2.5")
        BigDecimal packageWeightKg,

        @Schema(description = "Delivery service speed: STANDARD or EXPRESS", example = "STANDARD")
        String deliverySpeed
) {
}
