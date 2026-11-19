package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Order-service identity or a per-item error")
public record B2bOrderResult(
        @Schema(description = "Merchant reference") String externalRef,
        @Schema(description = "Order-service UUID") UUID orderId,
        @Schema(description = "Order-service tracking number") String trackingNumber,
        @Schema(description = "False when returning an existing order") boolean created,
        @Schema(description = "Validation or forwarding error; null on success") String error
) {}
