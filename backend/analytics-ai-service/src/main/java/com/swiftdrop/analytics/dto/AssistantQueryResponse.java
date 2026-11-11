package com.swiftdrop.analytics.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AI delivery assistant agent response with tool executions and actions")
public record AssistantQueryResponse(
        @Schema(description = "Synthesized response answer", example = "Estimated delivery is 14:45. Rain in Kandy adds approx 15 minutes. Recommend dispatching via Kurunegala hub.")
        String answer,

        @Schema(description = "Identified user intent", example = "ETA_INQUIRY")
        String detectedIntent,

        @Schema(description = "Deprecated compatibility field, always 0; no calibrated confidence is available", example = "0.0")
        double confidence,

        @Schema(description = "Quick operational action suggestions")
        List<String> suggestedActions,

        @Schema(description = "Audit trail of tools executed by the agent")
        List<String> toolInvocations
) {}
