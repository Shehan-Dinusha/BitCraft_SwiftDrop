package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Natural language prompt request to the AI delivery assistant agent")
public record AssistantQueryRequest(
        @NotBlank(message = "query is required")
        @Size(max = 1800)
        @Schema(description = "User query or operational prompt", example = "What is the ETA for parcel SWD-20261027-COL-0001 under current rain conditions in Kandy?")
        String query,

        @Schema(description = "Caller persona role (e.g. COURIER, ADMIN_OPS, CUSTOMER)", example = "ADMIN_OPS")
        @Size(max = 64) String callerPersona,

        @Schema(description = "Optional parcel tracking number context", example = "SWD-20261027-COL-0001")
        @Size(max = 64) String contextTrackingNumber
) {}
