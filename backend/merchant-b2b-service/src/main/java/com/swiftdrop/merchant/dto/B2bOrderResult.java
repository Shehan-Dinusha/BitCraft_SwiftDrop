package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Order-service identity or a per-item error")
public record B2bOrderResult(
        @Schema(description = "Merchant reference") String externalRef,
        @Schema(description = "Order-service UUID") UUID orderId,
        @Schema(description = "Order-service tracking number") String trackingNumber,
        @Schema(description = "False when returning an existing order") boolean created,
        @Schema(description = "Validation or forwarding error; null on success") String error,
        @Schema(description = "Delivery fee paid in cash at pickup", example = "350.00") java.math.BigDecimal fee,
        @Schema(description = "ISO currency", example = "LKR") String currency
) {
    public B2bOrderResult(String externalRef, UUID orderId, String trackingNumber, boolean created, String error) {
        this(externalRef, orderId, trackingNumber, created, error, null, null);
    }
}
