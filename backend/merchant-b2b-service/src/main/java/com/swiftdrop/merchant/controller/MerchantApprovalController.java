package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.merchant.domain.entity.MerchantStatus;
import com.swiftdrop.merchant.dto.MerchantApprovalResponse;
import com.swiftdrop.merchant.dto.RegisterMerchantRequest;
import com.swiftdrop.merchant.dto.RejectMerchantRequest;
import com.swiftdrop.merchant.security.MerchantAccess;
import com.swiftdrop.merchant.service.MerchantApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "MerchantApproval", description = "Merchant business applications, reviewed by the district dispatcher")
public class MerchantApprovalController {
    private final MerchantApprovalService approvals;

    @PostMapping("/api/v1/merchants/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit the caller's business application (MERCHANT)",
            description = "One application per merchant user. After a rejection, call again with the corrected details "
                    + "to resubmit. 409 while it is under review or once the business is registered.")
    public ApiResponse<MerchantApprovalResponse> register(@Valid @RequestBody RegisterMerchantRequest request,
                                                          HttpServletRequest httpRequest) {
        CallerContext caller = MerchantAccess.caller(httpRequest);
        caller.requireRole(UserRole.MERCHANT);
        return ApiResponse.success(approvals.register(caller.userId(), request), "Business pending approval");
    }

    @GetMapping("/api/v1/merchants/me/application")
    @Operation(summary = "Get the caller's business application (MERCHANT)",
            description = "Its status, and the reason when it was rejected. 404 before the first application.")
    public ApiResponse<MerchantApprovalResponse> myApplication(HttpServletRequest httpRequest) {
        CallerContext caller = MerchantAccess.caller(httpRequest);
        caller.requireRole(UserRole.MERCHANT);
        return ApiResponse.success(approvals.myApplication(caller.userId()), "Application retrieved");
    }

    @GetMapping("/api/v1/admin/merchants")
    @Operation(summary = "List merchant applications (DISPATCHER, SYSTEM_ADMIN)",
            description = "A DISPATCHER sees the applications of their own district (the district parameter is "
                    + "ignored); a SYSTEM_ADMIN sees every district or the one given.")
    public ApiResponse<List<MerchantApprovalResponse>> list(
            @RequestParam(required = false) MerchantStatus status,
            @RequestParam(required = false) SriLankanDistrict district,
            HttpServletRequest httpRequest) {
        CallerContext caller = MerchantAccess.requireOperations(httpRequest);
        return ApiResponse.success(approvals.list(caller, status, district), "Merchant applications retrieved");
    }

    @PostMapping("/api/v1/admin/merchants/{merchantId}/approve")
    @Operation(summary = "Approve a pending merchant (DISPATCHER of its district)")
    public ApiResponse<MerchantApprovalResponse> approve(
            @PathVariable UUID merchantId,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) UUID correlationId,
            HttpServletRequest httpRequest) {
        CallerContext caller = MerchantAccess.requireDispatcher(httpRequest);
        return ApiResponse.success(approvals.approve(caller, merchantId, correlationId), "Merchant approved");
    }

    @PostMapping("/api/v1/admin/merchants/{merchantId}/reject")
    @Operation(summary = "Reject a pending merchant with a reason (DISPATCHER of its district)",
            description = "The owner is emailed the reason and can correct the application and resubmit it.")
    public ApiResponse<MerchantApprovalResponse> reject(
            @PathVariable UUID merchantId,
            @Valid @RequestBody RejectMerchantRequest request,
            HttpServletRequest httpRequest) {
        CallerContext caller = MerchantAccess.requireDispatcher(httpRequest);
        return ApiResponse.success(approvals.reject(caller, merchantId, request.reason()), "Merchant rejected");
    }

    @PostMapping("/api/v1/admin/merchants/{merchantId}/suspend")
    @Operation(summary = "Suspend an approved merchant (SYSTEM_ADMIN)",
            description = "Its API keys stop working and it cannot upload orders until reactivated.")
    public ApiResponse<MerchantApprovalResponse> suspend(@PathVariable UUID merchantId, HttpServletRequest httpRequest) {
        MerchantAccess.caller(httpRequest).requireRole(UserRole.SYSTEM_ADMIN);
        return ApiResponse.success(approvals.suspend(merchantId), "Merchant suspended");
    }

    @PostMapping("/api/v1/admin/merchants/{merchantId}/reactivate")
    @Operation(summary = "Reactivate a suspended merchant (SYSTEM_ADMIN)")
    public ApiResponse<MerchantApprovalResponse> reactivate(@PathVariable UUID merchantId, HttpServletRequest httpRequest) {
        MerchantAccess.caller(httpRequest).requireRole(UserRole.SYSTEM_ADMIN);
        return ApiResponse.success(approvals.reactivate(merchantId), "Merchant reactivated");
    }
}
