package com.swiftdrop.dispatch.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.dispatch.dto.InternalMerchantParcel;
import com.swiftdrop.dispatch.dto.InternalPickupReadyRequest;
import com.swiftdrop.dispatch.service.MerchantPickupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Service-to-service only, called by the merchant service, which checks the merchant. Never routed by the gateway. */
@RestController
@RequestMapping("/internal/v1/merchant-pickups/{merchantId}")
@Tag(name = "Internal Merchant Pickup", description = "Service-to-service endpoints. Trusted network only.")
public class InternalMerchantPickupController {

    private final MerchantPickupService merchantPickupService;

    public InternalMerchantPickupController(MerchantPickupService merchantPickupService) {
        this.merchantPickupService = merchantPickupService;
    }

    @GetMapping
    @Operation(summary = "The shop's parcels that no courier has collected yet")
    public ApiResponse<List<InternalMerchantParcel>> atShop(@PathVariable UUID merchantId) {
        return ApiResponse.success(merchantPickupService.atShop(merchantId), "Parcels retrieved");
    }

    @PostMapping("/ready")
    @Operation(summary = "The shop says everything it has booked so far is packed")
    public ApiResponse<List<InternalMerchantParcel>> ready(@PathVariable UUID merchantId,
                                                           @Valid @RequestBody InternalPickupReadyRequest request) {
        return ApiResponse.success(merchantPickupService.markReady(merchantId, request.merchantName().trim()), "Marked ready");
    }
}
