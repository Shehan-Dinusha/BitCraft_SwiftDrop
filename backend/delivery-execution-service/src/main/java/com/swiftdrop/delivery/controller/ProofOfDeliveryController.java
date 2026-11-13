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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/{deliveryId}/proof-of-delivery", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload proof-of-delivery evidence via multipart/form-data",
               description = "Accepts photo (<= 5MB, jpeg/png), recipientName, optional signature and OTP")
    public ApiResponse<ProofOfDeliveryResponse> uploadMultipart(
            @PathVariable UUID deliveryId,
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("recipientName") String recipientName,
            @RequestParam(value = "signature", required = false) String signature,
            @RequestParam(value = "otp", required = false) String otp,
            HttpServletRequest httpRequest) {
        CallerContext caller = resolveCaller(httpRequest);
        if (caller != null && deliveryService != null) {
            deliveryService.assertCanAccess(deliveryId, caller);
        }
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Proof photo file is required");
        }
        if (photo.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Photo exceeds maximum allowed size of 5 MB");
        }
        String contentType = photo.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg") && !contentType.equalsIgnoreCase("image/png") && !contentType.equalsIgnoreCase("image/jpg"))) {
            throw new IllegalArgumentException("Only JPEG and PNG images are allowed");
        }
        if (recipientName == null || recipientName.isBlank()) {
            throw new IllegalArgumentException("Recipient name is required");
        }
        ProofOfDeliveryResponse response = storageService.storeMultipart(deliveryId, photo, recipientName, signature, otp);
        return ApiResponse.success(response, "Proof-of-delivery evidence uploaded");
    }

    @PostMapping(value = "/{deliveryId}/proof-of-delivery", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload proof-of-delivery evidence via JSON")
    public ApiResponse<ProofOfDeliveryResponse> uploadJson(
            @PathVariable UUID deliveryId,
            @RequestBody ProofOfDeliveryUploadRequest request,
            HttpServletRequest httpRequest) {
        CallerContext caller = resolveCaller(httpRequest);
        if (caller != null && deliveryService != null) {
            deliveryService.assertCanAccess(deliveryId, caller);
        }
        ProofOfDeliveryResponse response = storageService.store(deliveryId, request);
        return ApiResponse.success(response, "Proof-of-delivery evidence uploaded");
    }

    @PostMapping("/{deliveryId}/proof-of-delivery/verify")
    @Operation(summary = "Verify proof-of-delivery with recipient OTP")
    public ApiResponse<ProofOfDeliveryResponse> verify(
            @PathVariable UUID deliveryId,
            @RequestParam String otp,
            HttpServletRequest httpRequest) {
        CallerContext caller = resolveCaller(httpRequest);
        if (caller != null && deliveryService != null) {
            deliveryService.assertCanAccess(deliveryId, caller);
        }
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
