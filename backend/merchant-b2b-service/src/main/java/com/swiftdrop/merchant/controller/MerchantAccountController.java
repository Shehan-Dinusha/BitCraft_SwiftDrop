package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.merchant.security.MerchantAccess;
import jakarta.servlet.http.HttpServletRequest;
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
    @Operation(summary = "Provision a merchant account directly (SYSTEM_ADMIN)",
            description = "Businesses normally apply through POST /api/v1/merchants/register.")
    public ApiResponse<MerchantAccountResponse> createMerchant(@Valid @RequestBody CreateMerchantRequest request,
                                                               HttpServletRequest httpRequest) {
        MerchantAccess.caller(httpRequest).requireRole(UserRole.SYSTEM_ADMIN);
        MerchantAccountResponse response = merchantAccountService.createMerchant(request);
        return ApiResponse.success(response, "Merchant account successfully created");
    }

    @GetMapping("/{merchantId}")
    @Operation(summary = "Get merchant profile by UUID",
            description = "The merchant's own users, the dispatcher of its district, or a SYSTEM_ADMIN.")
    public ApiResponse<MerchantAccountResponse> getMerchant(@PathVariable UUID merchantId,
                                                            HttpServletRequest httpRequest) {
        MerchantAccountResponse response = merchantAccountService.getMerchantById(merchantId);
        MerchantAccess.requireOwnMerchantOrOperations(httpRequest, merchantId, response.operatingDistrict());
        return ApiResponse.success(response, "Merchant profile retrieved");
    }

    @GetMapping
    @Operation(summary = "List all merchant accounts (SYSTEM_ADMIN)")
    public ApiResponse<List<MerchantAccountResponse>> listMerchants(HttpServletRequest httpRequest) {
        MerchantAccess.caller(httpRequest).requireRole(UserRole.SYSTEM_ADMIN);
        List<MerchantAccountResponse> list = merchantAccountService.getAllMerchants();
        return ApiResponse.success(list, "Merchants retrieved");
    }
}
