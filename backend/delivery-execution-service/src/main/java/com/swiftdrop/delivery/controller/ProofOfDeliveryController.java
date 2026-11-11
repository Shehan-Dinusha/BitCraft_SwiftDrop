package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@Tag(name = "Proof of Delivery", description = "Delivery evidence and verification operations")
public class ProofOfDeliveryController {

    private final ProofOfDeliveryStorageService storageService;
    private final DeliveryExecutionService deliveryService;

    public ProofOfDeliveryController(ProofOfDeliveryStorageService storageService,
                                     @Autowired(required = false) DeliveryExecutionService deliveryService) {
        this.storageService = storageService;
        this.deliveryService = deliveryService;
    }

    @PostMapping("/{deliveryId}/proof-of-delivery")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload proof-of-delivery evidence")
    public ApiResponse<ProofOfDeliveryResponse> upload(
            @PathVariable UUID deliveryId,
            @RequestBody ProofOfDeliveryUploadRequest request) {
        ProofOfDeliveryResponse response = storageService.store(deliveryId, request);
        return ApiResponse.success(response, "Proof-of-delivery evidence uploaded");
    }

    @PostMapping("/{deliveryId}/proof-of-delivery/verify")
    @Operation(summary = "Verify proof-of-delivery with recipient OTP")
    public ApiResponse<ProofOfDeliveryResponse> verify(
            @PathVariable UUID deliveryId,
            @RequestParam String otp) {
        ProofOfDeliveryResponse response = storageService.verify(deliveryId, otp);
        return ApiResponse.success(response, "Proof-of-delivery verified successfully");
    }

    @GetMapping("/{deliveryId}/proof-of-delivery")
    @Operation(summary = "Get proof-of-delivery status for a delivery")
    public ApiResponse<ProofOfDeliveryResponse> get(
            @PathVariable UUID deliveryId,
            HttpServletRequest request) {
        CallerContext caller = resolveCaller(request);
        if (caller != null && deliveryService != null) {
            deliveryService.assertCanAccess(deliveryId, caller);
        }
        ProofOfDeliveryResponse response = storageService.getByDeliveryId(deliveryId);
        return ApiResponse.success(response, "Proof-of-delivery retrieved");
    }

    private CallerContext resolveCaller(HttpServletRequest request) {
        if (request == null || request.getHeader(JwtHeaderConstants.X_USER_ID) == null) {
            return null;
        }
        return CallerContext.from(request::getHeader);
    }
}
