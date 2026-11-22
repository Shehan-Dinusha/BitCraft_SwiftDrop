package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Merchant order receipt; delivery status is provided by public tracking")
public record MerchantOrderReceiptResponse(
        @Schema(description = "External order reference") String externalRef,
        @Schema(description = "Order service identifier") UUID orderId,
        @Schema(description = "Public tracking number") String trackingNumber,
        @Schema(description = "Submission time") Instant createdAt,
        @Schema(description = "Delivery fee paid in cash at pickup; null for older orders", example = "350.00") java.math.BigDecimal fee,
        @Schema(description = "ISO currency", example = "LKR") String currency) {}
