package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.merchant.dto.CreateMerchantRequest;
import com.swiftdrop.merchant.dto.MerchantAccountResponse;
import com.swiftdrop.merchant.service.MerchantAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
@Tag(name = "MerchantAccount", description = "Merchant business profile management operations")
public class MerchantAccountController {

    private final MerchantAccountService merchantAccountService;

    @GetMapping("/health")
    @Operation(summary = "Check merchant account controller health")
    public ApiResponse<String> health() {
        return ApiResponse.success("MerchantAccountController is active");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new merchant account")
    public ApiResponse<MerchantAccountResponse> createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        MerchantAccountResponse response = merchantAccountService.createMerchant(request);
        return ApiResponse.success(response, "Merchant account successfully created");
    }

    @GetMapping("/{merchantId}")
    @Operation(summary = "Get merchant profile by UUID")
    public ApiResponse<MerchantAccountResponse> getMerchant(@PathVariable UUID merchantId) {
        MerchantAccountResponse response = merchantAccountService.getOrCreateMerchant(merchantId, "SwiftDrop Partner");
        return ApiResponse.success(response, "Merchant profile retrieved");
    }

    @GetMapping
    @Operation(summary = "List all merchant accounts")
    public ApiResponse<List<MerchantAccountResponse>> listMerchants() {
        List<MerchantAccountResponse> list = merchantAccountService.getAllMerchants();
        return ApiResponse.success(list, "Merchants retrieved");
    }
}
