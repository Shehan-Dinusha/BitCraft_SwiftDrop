package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.merchant.domain.entity.MerchantStatus;
import com.swiftdrop.merchant.dto.MerchantApprovalResponse;
import com.swiftdrop.merchant.dto.RegisterMerchantRequest;
import com.swiftdrop.merchant.dto.RejectMerchantRequest;
import com.swiftdrop.merchant.service.MerchantApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "MerchantApproval", description = "Merchant business applications and admin verification")
public class MerchantApprovalController {
    private final MerchantApprovalService approvals;

    @PostMapping("/api/v1/merchants/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a business as the authenticated merchant")
    public ApiResponse<MerchantApprovalResponse> register(
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role,
            @Valid @RequestBody RegisterMerchantRequest request) {
        return ApiResponse.success(approvals.register(requireCaller(userId, role, "MERCHANT"), request),
                "Business pending approval");
    }

    @GetMapping("/api/v1/admin/merchants")
    @Operation(summary = "List merchant applications (SYSTEM_ADMIN only)")
    public ApiResponse<List<MerchantApprovalResponse>> list(
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role,
            @RequestParam(required = false) MerchantStatus status) {
        requireCaller(userId, role, "SYSTEM_ADMIN");
        return ApiResponse.success(approvals.list(status), "Merchant applications retrieved");
    }

    @PostMapping("/api/v1/admin/merchants/{merchantId}/approve")
    @Operation(summary = "Approve a pending merchant (SYSTEM_ADMIN only)")
    public ApiResponse<MerchantApprovalResponse> approve(
            @PathVariable UUID merchantId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) UUID correlationId) {
        UUID admin = requireCaller(userId, role, "SYSTEM_ADMIN");
        return ApiResponse.success(approvals.approve(merchantId, admin, correlationId), "Merchant approved");
    }

    @PostMapping("/api/v1/admin/merchants/{merchantId}/reject")
    @Operation(summary = "Reject a pending merchant with a reason (SYSTEM_ADMIN only)")
    public ApiResponse<MerchantApprovalResponse> reject(
            @PathVariable UUID merchantId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role,
            @Valid @RequestBody RejectMerchantRequest request) {
        requireCaller(userId, role, "SYSTEM_ADMIN");
        return ApiResponse.success(approvals.reject(merchantId, request.reason()), "Merchant rejected");
    }

    // The service must be internal: these headers are supplied by the authenticated gateway.
    private UUID requireCaller(String userId, String role, String expectedRole) {
        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated caller required");
        }
        UUID caller;
        try {
            caller = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid caller ID");
        }
        if (!expectedRole.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, expectedRole + " role required");
        }
        return caller;
    }
}
