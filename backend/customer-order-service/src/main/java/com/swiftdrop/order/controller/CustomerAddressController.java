package com.swiftdrop.order.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.order.dto.SavedAddressRequest;
import com.swiftdrop.order.dto.SavedAddressResponse;
import com.swiftdrop.order.exception.CallerIdentityRequiredException;
import com.swiftdrop.order.service.CustomerAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in customer's address book, for the pickup and drop-off steps of a booking. The owner is
 * always the caller from the gateway headers; no customer ID is ever taken from the request.
 */
@RestController
@RequestMapping("/api/v1/orders/addresses")
@Tag(name = "Address book", description = "Saved pickup and drop-off addresses of the signed-in customer")
@RequiredArgsConstructor
public class CustomerAddressController {

    private final CustomerAddressService addresses;

    @GetMapping
    @Operation(summary = "List my saved addresses, most recently changed first")
    public ApiResponse<List<SavedAddressResponse>> list(
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role) {
        return ApiResponse.success(addresses.list(customer(userId, role)), "Saved addresses retrieved");
    }

    @PostMapping
    @Operation(summary = "Save a new address", description = "Up to "
            + CustomerAddressService.MAX_ADDRESSES + " per customer; returns 409 when the book is full.")
    public ResponseEntity<ApiResponse<SavedAddressResponse>> create(
            @Valid @RequestBody SavedAddressRequest request,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(addresses.create(customer(userId, role), request), "Address saved"));
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Change a saved address")
    public ApiResponse<SavedAddressResponse> update(
            @PathVariable UUID addressId,
            @Valid @RequestBody SavedAddressRequest request,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role) {
        return ApiResponse.success(addresses.update(customer(userId, role), addressId, request), "Address updated");
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Delete a saved address")
    public ResponseEntity<Void> delete(
            @PathVariable UUID addressId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ID, required = false) String userId,
            @RequestHeader(value = JwtHeaderConstants.X_USER_ROLE, required = false) String role) {
        addresses.delete(customer(userId, role), addressId);
        return ResponseEntity.noContent().build();
    }

    /** The calling customer. Only customers keep an address book; staff and merchants get 403. */
    private static UUID customer(String userId, String role) {
        UUID id = HeaderParsing.parseUuidSafe(userId);
        if (id == null) {
            throw new CallerIdentityRequiredException("Customer identification is required");
        }
        if (UserRole.fromClaim(role) != UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only customers have an address book");
        }
        return id;
    }
}
