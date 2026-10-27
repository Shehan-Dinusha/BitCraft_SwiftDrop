package com.swiftdrop.merchant.dto;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Bulk order row item detail")
public record BulkBatchItemResponse(
        @Schema(description = "Batch identifier")
        UUID batchId,

        @Schema(description = "Row number within file")
        int rowIndex,

        @Schema(description = "Generated tracking number (e.g. SD-COL-20261027-ABCD)")
        String trackingNumber,

        @Schema(description = "Created order UUID")
        UUID orderId,

        @Schema(description = "Recipient full name")
        String recipientName,

        @Schema(description = "Recipient telephone number")
        String recipientPhone,

        @Schema(description = "Delivery destination district")
        SriLankanDistrict deliveryDistrict,

        @Schema(description = "Delivery destination address")
        String deliveryAddress,

        @Schema(description = "Package weight in kg")
        BigDecimal packageWeightKg,

        @Schema(description = "Delivery service speed")
        String deliverySpeed,

        @Schema(description = "Processing item status: PENDING, SUCCESS, FAILED")
        String itemStatus,

        @Schema(description = "Error details if failed")
        String errorMessage,

        @Schema(description = "Record creation timestamp")
        Instant createdAt
) {
}
