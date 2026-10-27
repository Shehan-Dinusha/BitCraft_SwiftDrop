package com.swiftdrop.webhook.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.webhook.dto.CreateWebhookEndpointRequest;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.dto.WebhookEndpointResponse;
import com.swiftdrop.webhook.service.WebhookSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/webhooks/subscriptions", "/api/v1/webhook-subscription"})
@Tag(name = "WebhookSubscription", description = "Webhook subscription registration, testing, and lifecycle management")
@RequiredArgsConstructor
public class WebhookSubscriptionController {

    private final WebhookSubscriptionService subscriptionService;

    @GetMapping("/health")
    @Operation(summary = "Health check for webhook subscription controller")
    public ApiResponse<String> health() {
        return ApiResponse.success("WebhookSubscriptionController is ready", "Health check OK");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new webhook subscription endpoint")
    public ApiResponse<WebhookEndpointResponse> createEndpoint(
            @Valid @RequestBody CreateWebhookEndpointRequest request) {
        log.info("Received request to register webhook endpoint for merchant: {}", request.merchantId());
        WebhookEndpointResponse response = subscriptionService.createEndpoint(request);
        return ApiResponse.success(response, "Webhook endpoint created successfully");
    }

    @GetMapping("/{endpointId}")
    @Operation(summary = "Get webhook endpoint details by ID")
    public ApiResponse<WebhookEndpointResponse> getEndpoint(
            @PathVariable @Parameter(description = "Endpoint UUID") UUID endpointId) {
        WebhookEndpointResponse response = subscriptionService.getEndpoint(endpointId);
        return ApiResponse.success(response, "Webhook endpoint retrieved");
    }

    @GetMapping
    @Operation(summary = "List webhook endpoints for a merchant or all if admin")
    public ApiResponse<List<WebhookEndpointResponse>> listEndpoints(
            @RequestParam(required = false) UUID merchantId,
            @RequestHeader(value = "X-Merchant-Id", required = false) String headerMerchantId) {

        UUID targetMerchant = merchantId;
        if (targetMerchant == null && headerMerchantId != null && !headerMerchantId.isBlank()) {
            try {
                targetMerchant = UUID.fromString(headerMerchantId);
            } catch (IllegalArgumentException ignored) {
            }
        }

        List<WebhookEndpointResponse> response;
        if (targetMerchant != null) {
            response = subscriptionService.listEndpointsByMerchant(targetMerchant);
        } else {
            response = subscriptionService.listAllEndpoints();
        }

        return ApiResponse.success(response, "Webhook endpoints retrieved");
    }

    @PatchMapping("/{endpointId}/status")
    @Operation(summary = "Toggle active status for a webhook endpoint")
    public ApiResponse<WebhookEndpointResponse> toggleActive(
            @PathVariable UUID endpointId,
            @RequestParam boolean active) {
        WebhookEndpointResponse response = subscriptionService.toggleEndpointActive(endpointId, active);
        return ApiResponse.success(response, "Endpoint status updated to " + (active ? "ACTIVE" : "INACTIVE"));
    }

    @DeleteMapping("/{endpointId}")
    @Operation(summary = "Delete a webhook endpoint subscription")
    public ApiResponse<Void> deleteEndpoint(@PathVariable UUID endpointId) {
        subscriptionService.deleteEndpoint(endpointId);
        return ApiResponse.success(null, "Webhook endpoint deleted successfully");
    }

    @PostMapping("/{endpointId}/test")
    @Operation(summary = "Dispatch a test ping to the webhook endpoint")
    public ApiResponse<WebhookDeliveryAttemptResponse> testPing(@PathVariable UUID endpointId) {
        log.info("Triggering test ping for endpoint: {}", endpointId);
        WebhookDeliveryAttemptResponse response = subscriptionService.testPing(endpointId);
        return ApiResponse.success(response, "Test ping dispatched");
    }

    @GetMapping("/{endpointId}/attempts")
    @Operation(summary = "List delivery attempts and history for an endpoint")
    public ApiResponse<List<WebhookDeliveryAttemptResponse>> getAttempts(@PathVariable UUID endpointId) {
        List<WebhookDeliveryAttemptResponse> response = subscriptionService.getDeliveryAttempts(endpointId);
        return ApiResponse.success(response, "Delivery attempts retrieved");
    }
}
