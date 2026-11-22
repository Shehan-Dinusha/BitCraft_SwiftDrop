package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.merchant.config.OpenApiConfig;
import com.swiftdrop.merchant.dto.MerchantPickupResponse;
import com.swiftdrop.merchant.security.MerchantAccess;
import com.swiftdrop.merchant.service.MerchantPickupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "MerchantPickups", description = "A shop's parcels waiting for a courier")
public class MerchantPickupController {

    private final MerchantPickupService pickups;

    @GetMapping("/api/v1/merchants/{merchantId}/pickup")
    @Operation(summary = "The shop's parcels that no courier has collected, and the cash to prepare")
    public ApiResponse<MerchantPickupResponse> current(@PathVariable UUID merchantId, HttpServletRequest request) {
        MerchantAccess.requireOwnMerchant(request, merchantId);
        return ApiResponse.success(pickups.current(merchantId), "Pickup retrieved");
    }

    @PostMapping("/api/v1/merchants/{merchantId}/pickup/ready")
    @Operation(summary = "Everything the shop has booked so far is packed", description = "Safe to repeat.")
    public ApiResponse<MerchantPickupResponse> ready(@PathVariable UUID merchantId, HttpServletRequest request) {
        MerchantAccess.requireOwnMerchant(request, merchantId);
        return ApiResponse.success(pickups.ready(merchantId), "Ready for pickup");
    }

    @PostMapping("/api/v1/b2b/pickups/ready")
    @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME)
    @Operation(summary = "The same, for a merchant's own system", description = "Authenticated by API key. Safe to repeat.")
    public ApiResponse<MerchantPickupResponse> readyByApiKey(
            @RequestHeader(value = JwtHeaderConstants.X_MERCHANT_ID, required = false) UUID merchantId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role) {
        if (merchantId == null || role == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated merchant context required");
        }
        if (!"MERCHANT".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MERCHANT role required");
        }
        return ApiResponse.success(pickups.ready(merchantId), "Ready for pickup");
    }
}
