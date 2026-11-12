package com.swiftdrop.analytics.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.analytics.ai.*;
import com.swiftdrop.analytics.ai.tools.ToolExecutor;
import com.swiftdrop.analytics.dto.McpRequest;
import com.swiftdrop.analytics.dto.McpResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.*;

/** Stateless MCP Streamable HTTP, JSON responses only. Identity is supplied by the trusted gateway. */
@RestController
@RequestMapping("/api/v1/ai/mcp")
@Tag(name = "DeliveryMcp", description = "Customer-only MCP tools; JSON-RPC envelopes rather than ApiResponse")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class AiMcpController {
    private static final String VERSION = "2025-03-26";
    private final ToolExecutor tools;
    private final ChatSessionStore limits;
    private final ObjectMapper mapper;

    public AiMcpController(ToolExecutor tools, ChatSessionStore limits, ObjectMapper mapper) {
        this.tools = tools;
        this.limits = limits;
        this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Initialize MCP, list tools, or call one validated, audited tool")
    public ResponseEntity<?> post(@RequestBody McpRequest request, HttpServletRequest servlet) {
        var context = authorize(servlet);
        if (!"2.0".equals(request.jsonrpc()) || request.method() == null || request.method().isBlank()
                || (request.id() != null && !(request.id().isTextual() || request.id().isIntegralNumber()))) {
            return rpcError(null, -32600, "Invalid Request");
        }
        JsonNode id = request.id();
        String method = request.method();
        if (id == null) return ResponseEntity.accepted().build();
        JsonNode params = request.params() == null ? mapper.createObjectNode() : request.params();
        Object result;
        switch (method) {
            case "initialize" -> {
                if (!params.path("protocolVersion").isTextual() || !params.path("capabilities").isObject()
                        || !params.path("clientInfo").isObject()) return rpcError(id, -32602, "Invalid params");
                result = Map.of("protocolVersion", VERSION, "capabilities", Map.of("tools", Map.of("listChanged", false)),
                        "serverInfo", Map.of("name", "swiftdrop-delivery-tools", "version", "1.0.0"));
            }
            case "ping" -> result = Map.of();
            case "tools/list" -> result = Map.of("tools", ToolExecutor.definitions().stream().map(def -> {
                var function = (Map<?, ?>) def.get("function");
                return Map.of("name", function.get("name"), "description", function.get("description"),
                        "inputSchema", function.get("parameters"));
            }).toList());
            case "tools/call" -> {
                if (!params.path("name").isTextual() || !params.path("arguments").isObject()) {
                    return rpcError(id, -32602, "Invalid params");
                }
                limits.consumeRequest(context.userId());
                var output = tools.execute(new LlmClient.ToolCall(id.asText(), params.path("name").asText(),
                        params.get("arguments")), context);
                result = Map.of("content", List.of(Map.of("type", "text", "text", encode(output))),
                        "isError", !"OK".equals(output.get("outcome")));
            }
            default -> { return rpcError(id, -32601, "Method not found"); }
        }
        return ResponseEntity.ok(new McpResponse("2.0", id, result, null));
    }

    @GetMapping
    @Operation(summary = "No server-initiated SSE stream is offered")
    public ResponseEntity<Void> get(HttpServletRequest servlet) {
        authorize(servlet);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(HttpMethod.POST).build();
    }

    private static ToolRequestContext authorize(HttpServletRequest servlet) {
        // Native MCP clients send no Origin. Browser-origin MCP connections are deliberately unsupported.
        if (servlet.getHeader("Origin") != null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Browser origins are not supported");
        var context = AiChatService.context(servlet);
        if (!"CUSTOMER".equals(context.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account required");
        return context;
    }

    private static ResponseEntity<?> rpcError(JsonNode id, int code, String message) {
        return ResponseEntity.ok(new McpResponse("2.0", id, null, new McpResponse.ProtocolError(code, message)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed(HttpServletRequest servlet) {
        authorize(servlet);
        return rpcError(null, -32700, "Parse error");
    }

    private String encode(Map<String, Object> output) {
        try { return mapper.writeValueAsString(output); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Cannot encode safe tool result"); }
    }
}
