package com.swiftdrop.delivery.controller;

import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
    public ProofOfDeliveryResponse upload(@PathVariable UUID deliveryId,
                                           @RequestBody ProofOfDeliveryUploadRequest request) {
        return storageService.store(deliveryId, request);
    }

    @PostMapping("/{deliveryId}/proof-of-delivery/verify")
    @Operation(summary = "Verify proof-of-delivery with recipient OTP")
    public ProofOfDeliveryResponse verify(@PathVariable UUID deliveryId,
                                          @RequestParam String otp) {
        return storageService.verify(deliveryId, otp);
    }
}
