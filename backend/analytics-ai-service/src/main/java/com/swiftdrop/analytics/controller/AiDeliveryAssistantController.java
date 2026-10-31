package com.swiftdrop.analytics.controller;

import com.swiftdrop.analytics.dto.*;
import com.swiftdrop.analytics.service.AiDeliveryAssistantService;
import com.swiftdrop.contracts.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/analytics/ai", "/api/v1/ai-delivery-assistant"})
@Tag(name = "AiDeliveryAssistant", description = "AI Delivery Assistant and Predictive Route Optimization API")
public class AiDeliveryAssistantController {

    private final AiDeliveryAssistantService assistantService;

    public AiDeliveryAssistantController(AiDeliveryAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint for AI assistant service")
    public ApiResponse<String> health() {
        return ApiResponse.success("AiDeliveryAssistantController is ready");
    }

    @PostMapping("/predict-delay")
    @Operation(summary = "Calculate predictive delay risk score and operational mitigation")
    public ApiResponse<DelayRiskPredictionResponse> predictDelayRisk(
            @Valid @RequestBody DelayRiskPredictionRequest request) {
        return ApiResponse.success(assistantService.predictDelayRisk(request), "Delay risk predicted successfully");
    }

    @PostMapping("/reroute-suggestions")
    @Operation(summary = "Generate dynamic transit reroute suggestions for delayed parcels")
    public ApiResponse<AnomalyRerouteResponse> suggestReroute(
            @Valid @RequestBody AnomalyRerouteRequest request) {
        return ApiResponse.success(assistantService.suggestReroute(request), "Reroute suggestions calculated");
    }

    @PostMapping("/agent-query")
    @Operation(summary = "Query the AI logistics assistant agent in natural language")
    public ApiResponse<AssistantQueryResponse> queryAgent(
            @Valid @RequestBody AssistantQueryRequest request) {
        return ApiResponse.success(assistantService.processAgentQuery(request), "Agent query processed");
    }

    @GetMapping("/audits")
    @Operation(summary = "Retrieve recent AI tool execution audit logs for compliance")
    public ApiResponse<List<AiAuditResponse>> getAudits() {
        return ApiResponse.success(assistantService.listRecentAudits(), "Recent AI audits retrieved");
    }
}
