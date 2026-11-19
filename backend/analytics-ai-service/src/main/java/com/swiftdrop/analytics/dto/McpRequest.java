package com.swiftdrop.analytics.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Protocol validation is performed by the adapter so errors retain JSON-RPC envelopes. */
@Schema(description = "MCP JSON-RPC request or notification. Omit id for notifications.")
public record McpRequest(
        @NotBlank @Schema(description = "JSON-RPC version", allowableValues = "2.0") String jsonrpc,
        @Schema(description = "String or integer request ID; absent for notifications") JsonNode id,
        @NotBlank @Schema(description = "MCP method name") String method,
        @Schema(description = "Method-specific arguments") JsonNode params) {}
