package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "AI tool invocation audit record for compliance and observability")
public record AiAuditResponse(
        @Schema(description = "Audit log UUID", example = "e1000000-0000-0000-0000-000000000001")
        UUID auditId,

        @Schema(description = "Tool or model name executed", example = "CourierEtaCalculationTool")
        String toolName,

        @Schema(description = "Caller persona or role", example = "ADMIN_OPS")
        String callerPersona,

        @Schema(description = "Deprecated compatibility field; always null, raw prompts are never stored", nullable = true)
        String promptOrQuery,

        @Schema(description = "Safe outcome code only", example = "OK")
        String outputResult,

        @Schema(description = "Execution duration in milliseconds", example = "14")
        long executionTimeMs,

        @Schema(description = "Whether tool completed successfully", example = "true")
        boolean success,

        @Schema(description = "Invocation timestamp")
        Instant createdAt
) {}
