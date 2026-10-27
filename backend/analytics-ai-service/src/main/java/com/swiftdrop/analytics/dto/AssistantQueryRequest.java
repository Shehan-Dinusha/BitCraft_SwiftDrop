package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Natural language prompt request to the AI delivery assistant agent")
public record AssistantQueryRequest(
        @NotBlank(message = "query is required")
        @Schema(description = "User query or operational prompt", example = "What is the ETA for parcel SWD-20261027-COL-0001 under current rain conditions in Kandy?")
        String query,

        @Schema(description = "Caller persona role (e.g. COURIER, ADMIN_OPS, CUSTOMER)", example = "ADMIN_OPS")
        String callerPersona,

        @Schema(description = "Optional parcel tracking number context", example = "SWD-20261027-COL-0001")
        String contextTrackingNumber
) {}
