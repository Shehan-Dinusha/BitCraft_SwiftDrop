package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.dto.CourierLocationUpdatePayload;
import com.swiftdrop.delivery.dto.DeliverySessionResponse;
import com.swiftdrop.delivery.dto.TrackingBreadcrumbResponse;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@Tag(name = "DeliveryExecution", description = "Delivery execution and courier transitions")
public class DeliveryExecutionController {

    private final DeliveryExecutionService deliveryService;

    public DeliveryExecutionController(DeliveryExecutionService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/health")
    @Operation(summary = "Check delivery execution service health")
    public ApiResponse<String> health() {
        return ApiResponse.success("UP", "DeliveryExecutionController is ready");
    }

    @GetMapping("/{deliveryId}")
    @Operation(summary = "Get delivery session by UUID")
    public ApiResponse<DeliverySessionResponse> getDeliveryById(@PathVariable UUID deliveryId) {
        return ApiResponse.success(deliveryService.getDeliveryById(deliveryId), "Delivery session retrieved");
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get delivery session by order UUID")
    public ApiResponse<DeliverySessionResponse> getDeliveryByOrderId(@PathVariable UUID orderId) {
        return ApiResponse.success(deliveryService.getDeliveryByOrderId(orderId), "Delivery session retrieved");
    }

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Get delivery session by tracking number")
    public ApiResponse<DeliverySessionResponse> getDeliveryByTrackingNumber(@PathVariable String trackingNumber) {
        return ApiResponse.success(deliveryService.getDeliveryByTrackingNumber(trackingNumber), "Delivery session retrieved");
    }

    @GetMapping("/courier/{courierId}")
    @Operation(summary = "List delivery sessions for a courier with optional state filter")
    public ApiResponse<List<DeliverySessionResponse>> getDeliveriesByCourier(
            @PathVariable UUID courierId,
            @RequestParam(required = false) DeliveryState state) {
        return ApiResponse.success(deliveryService.getDeliveriesByCourier(courierId, state), "Courier deliveries retrieved");
    }

    @PostMapping("/{deliveryId}/transition")
    @Operation(summary = "Transition delivery lifecycle state", description = "Executes courier mobile action transitions governed by 12-state FSM.")
    public ApiResponse<DeliverySessionResponse> transition(
            @PathVariable UUID deliveryId,
            @Valid @RequestBody TransitionDeliveryRequest request) {
        var session = deliveryService.transition(deliveryId, request);
        return ApiResponse.success(DeliverySessionResponse.fromEntity(session), "Delivery transitioned to " + session.getCurrentState());
    }

    @PostMapping("/{deliveryId}/location")
    @Operation(summary = "Record courier GPS telemetry breadcrumb")
    public ApiResponse<TrackingBreadcrumbResponse> recordLocation(
            @PathVariable UUID deliveryId,
            @Valid @RequestBody CourierLocationUpdatePayload payload) {
        var breadcrumb = deliveryService.recordLocation(deliveryId, payload);
        return ApiResponse.success(TrackingBreadcrumbResponse.fromEntity(breadcrumb), "Location breadcrumb recorded");
    }

    @GetMapping("/{deliveryId}/breadcrumbs")
    @Operation(summary = "Get recent GPS telemetry breadcrumbs for delivery")
    public ApiResponse<List<TrackingBreadcrumbResponse>> getBreadcrumbs(@PathVariable UUID deliveryId) {
        return ApiResponse.success(deliveryService.getBreadcrumbs(deliveryId), "Breadcrumbs retrieved");
    }
}
