package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Independent results for a bulk submission")
public record B2bBulkResponse(
        @Schema(description = "Total items") int total,
        @Schema(description = "Successful items, including duplicate retries") int successCount,
        @Schema(description = "Failed items") int failedCount,
        @Schema(description = "Results in input order") List<B2bOrderResult> items
) {}
