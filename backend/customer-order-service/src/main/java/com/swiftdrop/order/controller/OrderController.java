package com.swiftdrop.order.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.order.dto.CancelOrderRequest;
import com.swiftdrop.order.dto.CreateOrderRequest;
import com.swiftdrop.order.dto.CustomerOrderResponse;
import com.swiftdrop.order.dto.FeeEstimateRequest;
import com.swiftdrop.order.dto.FeeEstimateResponse;
import com.swiftdrop.order.dto.UpdateOrderRequest;
import com.swiftdrop.order.service.CustomerOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/orders", "/api/v1/order"})
@Tag(name = "Order", description = "Customer parcel ordering lifecycle, fee estimation, label printing, and fee revisions")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final CustomerOrderService customerOrderService;

    @GetMapping("/health")
    @Operation(summary = "Check order service health")
    public ApiResponse<String> health() {
        return ApiResponse.success("Customer Order Service is running");
    }

    @PostMapping("/estimate")
    @Operation(summary = "Calculate pre-confirmation delivery fee estimate (FR-C2)")
    public ApiResponse<FeeEstimateResponse> calculateEstimate(@Valid @RequestBody FeeEstimateRequest request) {
        return ApiResponse.success(customerOrderService.calculateEstimate(request), "Fee estimate calculated successfully");
    }

    @PostMapping
    @Operation(summary = "Create and book a new customer order (FR-C1)")
    public ApiResponse<CustomerOrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) String correlationIdHeader) {

        UUID authUserId = parseUuidSafe(userIdHeader);
        UUID correlationId = parseUuidSafe(correlationIdHeader);

        CustomerOrderResponse response = customerOrderService.createOrder(request, authUserId, correlationId);
        return ApiResponse.success(response, "Order created successfully with tracking number " + response.getTrackingNumber());
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order details by order UUID")
    public ApiResponse<CustomerOrderResponse> getOrderById(
            @PathVariable("orderId") UUID orderId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String userRoleHeader) {
        return ApiResponse.success(
                customerOrderService.getOrderById(orderId, parseUuidSafe(userIdHeader), userRoleHeader),
                "Order retrieved successfully");
    }

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Get order details by non-sequential tracking number")
    public ApiResponse<CustomerOrderResponse> getOrderByTrackingNumber(@PathVariable("trackingNumber") String trackingNumber) {
        return ApiResponse.success(customerOrderService.getOrderByTrackingNumber(trackingNumber), "Order retrieved successfully");
    }

    @GetMapping
    @Operation(summary = "List customer's orders with pagination")
    public ApiResponse<PagedResult<CustomerOrderResponse>> listOrders(
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String userRoleHeader,
            @RequestParam(value = "customerId", required = false) UUID customerIdParam,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        PagedResult<CustomerOrderResponse> result = customerOrderService.listCustomerOrders(
                customerIdParam, parseUuidSafe(userIdHeader), userRoleHeader, pageable);
        return ApiResponse.success(result, "Orders retrieved successfully");
    }

    @PutMapping("/{orderId}")
    @Operation(summary = "Edit order while in CREATED status")
    public ApiResponse<CustomerOrderResponse> updateOrder(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody UpdateOrderRequest request,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String userRoleHeader) {

        UUID authUserId = parseUuidSafe(userIdHeader);
        CustomerOrderResponse response = customerOrderService.updateOrder(orderId, request, authUserId, userRoleHeader);
        return ApiResponse.success(response, "Order updated successfully");
    }

    @DeleteMapping("/{orderId}")
    @Operation(summary = "Delete order while in CREATED status")
    public ApiResponse<String> deleteOrder(
            @PathVariable("orderId") UUID orderId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String userRoleHeader) {

        UUID authUserId = parseUuidSafe(userIdHeader);
        customerOrderService.deleteOrder(orderId, authUserId, userRoleHeader);
        return ApiResponse.success("Order " + orderId + " deleted successfully");
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Request order cancellation (FR-C3)")
    public ApiResponse<CustomerOrderResponse> cancelOrder(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody(required = false) CancelOrderRequest request,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String userRoleHeader,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) String correlationIdHeader) {

        UUID authUserId = parseUuidSafe(userIdHeader);
        UUID correlationId = parseUuidSafe(correlationIdHeader);
        CustomerOrderResponse response = customerOrderService.cancelOrder(orderId, request, authUserId, userRoleHeader, correlationId);
        return ApiResponse.success(response, "Order cancelled successfully");
    }

    @PostMapping("/{orderId}/fee-revision/accept")
    @Operation(summary = "Customer accepts courier pickup weight/fee revision (FR-C8, FR-K5)")
    public ApiResponse<CustomerOrderResponse> acceptFeeRevision(
            @PathVariable("orderId") UUID orderId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String userRoleHeader,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) String correlationIdHeader) {

        UUID authUserId = parseUuidSafe(userIdHeader);
        UUID correlationId = parseUuidSafe(correlationIdHeader);
        CustomerOrderResponse response = customerOrderService.acceptFeeRevision(orderId, authUserId, userRoleHeader, correlationId);
        return ApiResponse.success(response, "Fee revision accepted. Final fee is LKR " + response.getFinalFee());
    }

    @PostMapping("/{orderId}/fee-revision/reject")
    @Operation(summary = "Customer rejects courier fee revision (auto-cancels at $0) (FR-C8, FR-K5)")
    public ApiResponse<CustomerOrderResponse> rejectFeeRevision(
            @PathVariable("orderId") UUID orderId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String userRoleHeader,
            @RequestHeader(value = JwtHeaderConstants.X_CORRELATION_ID, required = false) String correlationIdHeader) {

        UUID authUserId = parseUuidSafe(userIdHeader);
        UUID correlationId = parseUuidSafe(correlationIdHeader);
        CustomerOrderResponse response = customerOrderService.rejectFeeRevision(orderId, authUserId, userRoleHeader, correlationId);
        return ApiResponse.success(response, "Fee revision rejected. Order has been cancelled at LKR 0.00");
    }

    @GetMapping(value = "/{orderId}/shipping-label", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download 2-page A5 shipping label with QR code and fee estimate receipt (FR-C4, FR-C5)")
    public ResponseEntity<byte[]> downloadShippingLabel(
            @Parameter(description = "Order UUID") @PathVariable("orderId") UUID orderId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String userRoleHeader) {

        byte[] pdfBytes = customerOrderService.getShippingLabelPdf(orderId, parseUuidSafe(userIdHeader), userRoleHeader);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"shipping-label-" + orderId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping(value = "/{orderId}/delivery-receipt", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download A5 final delivery & cash collection receipt (FR-C7)")
    public ResponseEntity<byte[]> downloadDeliveryReceipt(
            @Parameter(description = "Order UUID") @PathVariable("orderId") UUID orderId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String userRoleHeader) {

        byte[] pdfBytes = customerOrderService.getDeliveryReceiptPdf(orderId, parseUuidSafe(userIdHeader), userRoleHeader);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"delivery-receipt-" + orderId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private UUID parseUuidSafe(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return UUID.fromString(str.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
