package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.merchant.domain.repository.MerchantOrderReceiptRepository;
import com.swiftdrop.merchant.dto.MerchantOrderReceiptResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "MerchantOrders", description = "Merchant-scoped submitted order receipts")
public class MerchantOrderQueryController {
    private final MerchantOrderReceiptRepository receipts;

    @GetMapping("/api/v1/merchants/{merchantId}/orders")
    @Operation(summary = "List this merchant's submitted orders, newest first")
    public ApiResponse<List<MerchantOrderReceiptResponse>> list(
            @PathVariable UUID merchantId,
            @RequestHeader(value = JwtHeaderConstants.X_MERCHANT_ID, required = false) UUID callerMerchant,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (role == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated caller required");
        if (!"MERCHANT".equals(role) || !merchantId.equals(callerMerchant)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant identity does not match");
        }
        var result = receipts.findByMerchantIdOrderByCreatedAtDesc(merchantId,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return ApiResponse.success(result.map(r -> new MerchantOrderReceiptResponse(
                r.getExternalRef(), r.getOrderId(), r.getTrackingNumber(), r.getCreatedAt())).getContent(),
                "Merchant orders retrieved");
    }
}
