package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.iam.dto.CreateCourierRequest;
import com.swiftdrop.iam.dto.UpdateCourierRequest;
import com.swiftdrop.iam.dto.UpdateUserStatusRequest;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.security.SecurityPrincipal;
import com.swiftdrop.iam.service.CourierAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/couriers")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DISPATCHER')")
@Tag(name = "Courier accounts", description = "Courier identity management scoped to the dispatcher's district")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class CourierAccountController {
    private final CourierAccountService couriers;

    public CourierAccountController(CourierAccountService couriers) {
        this.couriers = couriers;
    }

    @GetMapping
    @Operation(summary = "List couriers in the caller's scope; administrators can see all districts")
    public ApiResponse<PagedResult<UserProfileResponse>> list(Authentication authentication,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(couriers.list(SecurityPrincipal.userId(authentication), status, search, page, size), "Couriers retrieved");
    }

    @GetMapping("/{courierId}")
    @Operation(summary = "Get a courier identity within the caller's scope")
    public ApiResponse<UserProfileResponse> get(Authentication authentication, @PathVariable UUID courierId) {
        return ApiResponse.success(couriers.get(SecurityPrincipal.userId(authentication), courierId), "Courier retrieved");
    }

    @PostMapping
    @Operation(summary = "Provision a courier identity within the caller's scope")
    public ResponseEntity<ApiResponse<UserProfileResponse>> create(Authentication authentication,
            @Valid @RequestBody CreateCourierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                couriers.create(SecurityPrincipal.userId(authentication), request), "Courier created"));
    }

    @PutMapping("/{courierId}")
    @Operation(summary = "Replace a courier's contact details within the caller's scope")
    public ApiResponse<UserProfileResponse> update(Authentication authentication, @PathVariable UUID courierId,
            @Valid @RequestBody UpdateCourierRequest request) {
        return ApiResponse.success(couriers.update(SecurityPrincipal.userId(authentication), courierId, request), "Courier updated");
    }

    @PatchMapping("/{courierId}/status")
    @Operation(summary = "Suspend, deactivate or reactivate a courier within the caller's scope")
    public ApiResponse<UserProfileResponse> updateStatus(Authentication authentication, @PathVariable UUID courierId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(couriers.updateStatus(SecurityPrincipal.userId(authentication), courierId, request.status()), "Courier status updated");
    }
}
