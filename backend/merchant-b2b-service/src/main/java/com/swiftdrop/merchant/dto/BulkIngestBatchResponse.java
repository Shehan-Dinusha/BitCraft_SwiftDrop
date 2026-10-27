package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Bulk order ingestion batch summary")
public record BulkIngestBatchResponse(
        @Schema(description = "Batch unique UUID")
        UUID batchId,

        @Schema(description = "Merchant UUID")
        UUID merchantId,

        @Schema(description = "Ingested file or reference name")
        String fileName,

        @Schema(description = "Batch status: PENDING, PROCESSING, COMPLETED, FAILED")
        String batchStatus,

        @Schema(description = "Total records in batch")
        int totalRecords,

        @Schema(description = "Successfully created and dispatched order count")
        int successCount,

        @Schema(description = "Failed record count")
        int failedCount,

        @Schema(description = "Summary of parsing or dispatch errors")
        String errorSummary,

        @Schema(description = "Batch submission timestamp")
        Instant createdAt,

        @Schema(description = "Batch completion timestamp")
        Instant completedAt
) {
}
