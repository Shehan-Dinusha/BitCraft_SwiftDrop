package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.MerchantApiKeyValidationRequest;
import com.swiftdrop.contracts.dto.MerchantApiKeyValidationResult;
import com.swiftdrop.merchant.dto.ApiKeyResponse;
import com.swiftdrop.merchant.dto.CreateApiKeyRequest;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "ApiKeyManagement", description = "Merchant API key provisioning, rotation, and gateway validation")
public class ApiKeyManagementController {

    private final MerchantApiKeyService apiKeyService;

    @GetMapping("/api/v1/api-key-management/health")
    @Operation(summary = "Check API key management controller health")
    public ApiResponse<String> health() {
        return ApiResponse.success("ApiKeyManagementController is active");
    }

    @PostMapping("/api/v1/merchants/{merchantId}/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generate a new merchant API key", description = "Returns the raw API key secret ONLY ONCE.")
    public ApiResponse<ApiKeyResponse> generateApiKey(
            @PathVariable UUID merchantId,
            @Valid @RequestBody CreateApiKeyRequest request) {
        ApiKeyResponse response = apiKeyService.generateApiKey(merchantId, request);
        return ApiResponse.success(response, "API key successfully generated. Store secret securely.");
    }

    @GetMapping("/api/v1/merchants/{merchantId}/api-keys")
    @Operation(summary = "List all API keys for a merchant", description = "Raw secret key is not returned.")
    public ApiResponse<List<ApiKeyResponse>> listApiKeys(@PathVariable UUID merchantId) {
        List<ApiKeyResponse> keys = apiKeyService.listApiKeys(merchantId);
        return ApiResponse.success(keys, "API keys retrieved successfully");
    }

    @DeleteMapping("/api/v1/merchants/{merchantId}/api-keys/{keyId}")
    @Operation(summary = "Revoke a merchant API key")
    public ApiResponse<ApiKeyResponse> revokeApiKey(
            @PathVariable UUID merchantId,
            @PathVariable UUID keyId) {
        ApiKeyResponse revoked = apiKeyService.revokeApiKey(merchantId, keyId);
        return ApiResponse.success(revoked, "API key successfully revoked");
    }

    /**
     * Internal endpoint invoked by API Gateway MerchantApiKeyGlobalFilter.
     * Must not be exposed to the public edge.
     */
    @PostMapping("/internal/v1/api-keys/validate")
    @Operation(summary = "Internal API key validation", description = "Invoked by API Gateway filter to authenticate merchant calls.")
    public ResponseEntity<MerchantApiKeyValidationResult> validateApiKey(
            @Valid @RequestBody MerchantApiKeyValidationRequest request) {
        MerchantApiKeyValidationResult result = apiKeyService.validateApiKey(request.apiKey());
        return ResponseEntity.ok(result);
    }
}
