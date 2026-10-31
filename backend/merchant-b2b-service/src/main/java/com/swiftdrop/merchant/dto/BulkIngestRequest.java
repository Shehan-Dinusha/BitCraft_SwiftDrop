package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Bulk order ingestion request batch")
public record BulkIngestRequest(
        @Schema(description = "Uploaded batch file or reference name", example = "orders_20261027_batch1.csv")
        String fileName,

        @NotEmpty(message = "Order list cannot be empty")
        @Valid
        @Schema(description = "List of delivery orders to ingest")
        List<BulkOrderIngestItem> orders
) {
}
