package com.swiftdrop.webhook.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
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
    @Operation(summary = "Register a new webhook subscription endpoint",
            description = "A MERCHANT registers under the merchant identity of its token; merchantId may be omitted and "
                    + "any other value is refused (403). A SYSTEM_ADMIN must supply merchantId. Other roles: 403.")
    public ApiResponse<WebhookEndpointResponse> createEndpoint(
            @Valid @RequestBody CreateWebhookEndpointRequest request, CallerContext caller) {
        WebhookEndpointResponse response = subscriptionService.createEndpoint(request, caller);
        return ApiResponse.success(response, "Webhook endpoint created successfully");
    }

    @GetMapping("/{endpointId}")
    @Operation(summary = "Get webhook endpoint details by ID",
            description = "Another merchant's endpoint returns 404, exactly like an unknown ID.")
    public ApiResponse<WebhookEndpointResponse> getEndpoint(
            @PathVariable @Parameter(description = "Endpoint UUID") UUID endpointId, CallerContext caller) {
        WebhookEndpointResponse response = subscriptionService.getEndpoint(endpointId, caller);
        return ApiResponse.success(response, "Webhook endpoint retrieved");
    }

    @GetMapping
    @Operation(summary = "List webhook endpoints",
            description = "A MERCHANT receives only its own endpoints (a different merchantId is refused with 403). "
                    + "A SYSTEM_ADMIN may filter by merchantId or omit it to list every endpoint.")
    public ApiResponse<List<WebhookEndpointResponse>> listEndpoints(
            @RequestParam(required = false) @Parameter(description = "Merchant filter; administrators only")
            UUID merchantId,
            CallerContext caller) {
        List<WebhookEndpointResponse> response = subscriptionService.listEndpoints(merchantId, caller);
        return ApiResponse.success(response, "Webhook endpoints retrieved");
    }

    @PatchMapping("/{endpointId}/status")
    @Operation(summary = "Toggle active status for a webhook endpoint")
    public ApiResponse<WebhookEndpointResponse> toggleActive(
            @PathVariable UUID endpointId,
            @RequestParam boolean active,
            CallerContext caller) {
        WebhookEndpointResponse response = subscriptionService.toggleEndpointActive(endpointId, active, caller);
        return ApiResponse.success(response, "Endpoint status updated to " + (active ? "ACTIVE" : "INACTIVE"));
    }

    @DeleteMapping("/{endpointId}")
    @Operation(summary = "Delete a webhook endpoint subscription")
    public ApiResponse<Void> deleteEndpoint(@PathVariable UUID endpointId, CallerContext caller) {
        subscriptionService.deleteEndpoint(endpointId, caller);
        return ApiResponse.success(null, "Webhook endpoint deleted successfully");
    }

    @PostMapping("/{endpointId}/test")
    @Operation(summary = "Dispatch a test ping to the webhook endpoint")
    public ApiResponse<WebhookDeliveryAttemptResponse> testPing(@PathVariable UUID endpointId, CallerContext caller) {
        log.info("Triggering test ping for endpoint: {}", endpointId);
        WebhookDeliveryAttemptResponse response = subscriptionService.testPing(endpointId, caller);
        return ApiResponse.success(response, "Test ping dispatched");
    }

    @GetMapping("/{endpointId}/attempts")
    @Operation(summary = "List delivery attempts and history for an endpoint")
    public ApiResponse<List<WebhookDeliveryAttemptResponse>> getAttempts(@PathVariable UUID endpointId,
                                                                         CallerContext caller) {
        List<WebhookDeliveryAttemptResponse> response = subscriptionService.getDeliveryAttempts(endpointId, caller);
        return ApiResponse.success(response, "Delivery attempts retrieved");
    }
}
