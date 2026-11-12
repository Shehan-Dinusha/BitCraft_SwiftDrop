package com.swiftdrop.merchant.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.merchant.dto.*;
import com.swiftdrop.merchant.service.B2bOrderSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/b2b/orders")
@Tag(name = "B2bOrders", description = "API-key caller identity must be authenticated and injected by the gateway")
@RequiredArgsConstructor
public class B2bOrderController {
    private final B2bOrderSubmissionService submissions;

    @PostMapping
    @Operation(summary = "Create a merchant order; retries return the existing order")
    public ResponseEntity<ApiResponse<B2bOrderResult>> create(
            @RequestHeader(value = JwtHeaderConstants.X_MERCHANT_ID, required = false) UUID merchantId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) UUID correlationId,
            @Valid @RequestBody B2bOrderRequest request) {
        requireMerchant(merchantId, role);
        var result = submissions.submit(merchantId, request, correlationId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result, "Merchant order accepted"));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Submit up to 500 orders with independent item results")
    public ApiResponse<B2bBulkResponse> bulk(
            @RequestHeader(value = JwtHeaderConstants.X_MERCHANT_ID, required = false) UUID merchantId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) UUID correlationId,
            @Valid @RequestBody B2bBulkRequest request) {
        requireMerchant(merchantId, role);
        UUID correlation = correlationId != null ? correlationId : UUID.randomUUID();
        List<B2bOrderResult> results = new ArrayList<>();
        for (B2bOrderRequest item : request.orders()) {
            try { results.add(submissions.submit(merchantId, item, correlation)); }
            catch (IllegalArgumentException ex) { results.add(failed(item, ex.getMessage())); }
            catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() == 403 || ex.getStatusCode().value() == 401) throw ex;
                results.add(failed(item, ex.getReason()));
            }
        }
        int failures = (int) results.stream().filter(item -> item.error() != null).count();
        return ApiResponse.success(new B2bBulkResponse(results.size(), results.size() - failures, failures, results), "Bulk processed");
    }

    private B2bOrderResult failed(B2bOrderRequest item, String error) {
        return new B2bOrderResult(item == null ? null : item.externalRef(), null, null, false, error);
    }

    private void requireMerchant(UUID merchantId, String role) {
        if (merchantId == null || role == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated merchant context required");
        if (!"MERCHANT".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MERCHANT role required");
    }
}
