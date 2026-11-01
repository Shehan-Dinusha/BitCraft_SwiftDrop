package com.swiftdrop.order.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.order.dto.InternalTrackingView;
import com.swiftdrop.order.dto.ValidationPatterns;
import com.swiftdrop.order.service.InternalTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Public-safe tracking view by tracking number",
            description = "For the gateway tracking hub. Returns status, status history and city/district only. "
                    + "Internal, no user context: must never be exposed through the API gateway.")
    public ApiResponse<InternalTrackingView> getTracking(
            @PathVariable("trackingNumber")
            @Pattern(regexp = ValidationPatterns.TRACKING_NUMBER, message = ValidationPatterns.TRACKING_NUMBER_MESSAGE)
            String trackingNumber) {
        return ApiResponse.success(internalTrackingService.getTrackingView(trackingNumber), "Tracking retrieved successfully");
    }
}
