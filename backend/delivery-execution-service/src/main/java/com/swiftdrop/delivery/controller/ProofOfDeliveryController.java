package com.swiftdrop.delivery.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public ProofOfDeliveryController(ProofOfDeliveryStorageService storageService) {
        this.storageService = storageService;
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
            @PathVariable UUID deliveryId) {
        ProofOfDeliveryResponse response = storageService.getByDeliveryId(deliveryId);
        return ApiResponse.success(response, "Proof-of-delivery retrieved");
    }
}
