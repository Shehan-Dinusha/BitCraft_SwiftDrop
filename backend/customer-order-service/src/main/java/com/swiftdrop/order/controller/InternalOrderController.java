package com.swiftdrop.order.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.InternalCreateOrderRequest;
import com.swiftdrop.order.dto.InternalTrackingView;
import com.swiftdrop.order.dto.ValidationPatterns;
import com.swiftdrop.order.service.InternalOrderService;
import com.swiftdrop.order.service.InternalTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service endpoints. They carry no user context (no {@code X-User-Id}) and are not routed
 * through the API gateway; they must stay reachable from the internal network only.
 */
@RestController
@RequestMapping("/internal/v1/orders")
@Tag(name = "Internal Order",
        description = "Service-to-service endpoints. Trusted network only: must never be exposed through the API gateway.")
@RequiredArgsConstructor
public class InternalOrderController {

    private final InternalTrackingService internalTrackingService;
    private final InternalOrderService internalOrderService;

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Public-safe tracking view by tracking number",
            description = "For the gateway tracking hub. Returns status, status history, city/district and masked sender and receiver contact only. "
                    + "Internal, no user context: must never be exposed through the API gateway.")
    public ApiResponse<InternalTrackingView> getTracking(
            @PathVariable("trackingNumber")
            @Pattern(regexp = ValidationPatterns.TRACKING_NUMBER, message = ValidationPatterns.TRACKING_NUMBER_MESSAGE)
            String trackingNumber) {
        return ApiResponse.success(internalTrackingService.getTrackingView(trackingNumber), "Tracking retrieved successfully");
    }

    @PostMapping
    @Operation(summary = "Create an order on behalf of a merchant (idempotent)",
            description = "Called by the merchant service. Idempotent on (merchantId, externalRef): the first call returns 201 "
                    + "with the new order and publishes order.created once; repeating it returns 200 with the same order and "
                    + "publishes nothing. The merchant id is stored as the order's customer id. Internal, no user context: "
                    + "must never be exposed through the API gateway.")
    public ResponseEntity<ApiResponse<CustomerOrderResponse>> createMerchantOrder(
            @Valid @RequestBody InternalCreateOrderRequest request,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) String correlationIdHeader) {

        InternalOrderService.CreateResult result =
                internalOrderService.createMerchantOrder(request, HeaderParsing.parseUuidSafe(correlationIdHeader));

        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(result.order(),
                            "Order created with tracking number " + result.order().getTrackingNumber()));
        }
        return ResponseEntity.ok(ApiResponse.success(result.order(),
                "Order already exists for this external reference"));
    }
}
