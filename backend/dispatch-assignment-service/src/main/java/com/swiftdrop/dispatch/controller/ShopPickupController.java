package com.swiftdrop.dispatch.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.dispatch.dto.AssignShopPickupRequest;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.dto.ShopCourierSuggestionResponse;
import com.swiftdrop.dispatch.security.DispatchAccess;
import com.swiftdrop.dispatch.service.CourierConflictRetry;
import com.swiftdrop.dispatch.service.MerchantPickupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatch/shops")
@Tag(name = "ShopPickup", description = "A shop's parcels, collected in one visit")
public class ShopPickupController {

    private final MerchantPickupService merchantPickupService;
    private final CourierConflictRetry courierConflictRetry;

    public ShopPickupController(MerchantPickupService merchantPickupService, CourierConflictRetry courierConflictRetry) {
        this.merchantPickupService = merchantPickupService;
        this.courierConflictRetry = courierConflictRetry;
    }

    @GetMapping("/{merchantId}/couriers")
    @Operation(summary = "Couriers on shift and which of the shop's ready parcels each has room for",
            description = "A DISPATCHER gets their own district.")
    public ApiResponse<List<ShopCourierSuggestionResponse>> couriers(
            @PathVariable UUID merchantId,
            HttpServletRequest http) {
        CallerContext caller = DispatchAccess.requireOperations(http);
        return ApiResponse.success(merchantPickupService.suggestions(caller, merchantId), "Couriers retrieved");
    }

    @PostMapping("/{merchantId}/assign")
    @Operation(summary = "Give a shop's ready parcels to one courier, all of them or none",
            description = "The same checks as a single assignment: district, vehicle limits and parcel count.")
    public ApiResponse<List<DispatchAssignmentResponse>> assign(
            @PathVariable UUID merchantId,
            @Valid @RequestBody AssignShopPickupRequest request,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) String correlationId,
            HttpServletRequest http) {
        CallerContext caller = DispatchAccess.requireOperations(http);
        UUID correlation = DispatchController.correlationIdOrNull(correlationId);
        return ApiResponse.success(courierConflictRetry.run("Shop pickup",
                () -> merchantPickupService.assign(caller, merchantId, request, correlation)), "Shop pickup assigned");
    }
}
