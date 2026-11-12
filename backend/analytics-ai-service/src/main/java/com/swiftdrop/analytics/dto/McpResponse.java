package com.swiftdrop.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "JSON-RPC response; exactly one of result or error is present.")
public record McpResponse(
        @Schema(description = "JSON-RPC version", allowableValues = "2.0") String jsonrpc,
        @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(description = "Request ID; null for invalid requests") JsonNode id,
        @Schema(description = "Method-specific MCP result") Object result,
        @Schema(description = "Protocol error") ProtocolError error) {
    @Schema(description = "Safe JSON-RPC protocol error")
    public record ProtocolError(
            @Schema(description = "JSON-RPC error code") int code,
            @Schema(description = "Safe error description") String message) {}
}
