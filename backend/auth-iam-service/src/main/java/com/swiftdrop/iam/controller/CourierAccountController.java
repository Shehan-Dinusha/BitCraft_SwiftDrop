package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.iam.dto.CreateCourierRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/couriers")
@Tag(name = "Courier accounts", description = "Dispatchers register and manage the couriers of their own district")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
@PreAuthorize("hasRole('DISPATCHER')")
public class CourierAccountController {

    private final CourierAccountService courierAccountService;

    public CourierAccountController(CourierAccountService courierAccountService) {
        this.courierAccountService = courierAccountService;
    }

    @GetMapping
    @Operation(summary = "List couriers",
            description = "A DISPATCHER sees the couriers of their own district.")
    public ApiResponse<PagedResult<UserProfileResponse>> listCouriers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        return ApiResponse.success(courierAccountService.list(SecurityPrincipal.userId(authentication),
                status, search, page, size), "Couriers retrieved");
    }

    @GetMapping("/{courierId}")
    @Operation(summary = "Get a courier", description = "403 for a courier of another district.")
    public ApiResponse<UserProfileResponse> getCourier(@PathVariable UUID courierId, Authentication authentication) {
        return ApiResponse.success(courierAccountService.get(SecurityPrincipal.userId(authentication), courierId),
                "Courier retrieved");
    }

    @PostMapping
    @Operation(summary = "Register a courier and email them an invite",
            description = "Creates the COURIER account in the dispatcher's district without a password. The courier "
                    + "receives an email link (valid 72 hours) where they choose a password and add their vehicle.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> registerCourier(
            @Valid @RequestBody CreateCourierRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                courierAccountService.register(SecurityPrincipal.userId(authentication), request),
                "Courier registered. An invite email is on its way."));
    }

    @PostMapping("/{courierId}/invite")
    @Operation(summary = "Resend a courier's invite",
            description = "Sends a new invite link and invalidates the previous one. 409 once the courier has set up the account.")
    public ApiResponse<UserProfileResponse> resendInvite(@PathVariable UUID courierId, Authentication authentication) {
        return ApiResponse.success(
                courierAccountService.resendInvite(SecurityPrincipal.userId(authentication), courierId),
                "Invite sent");
    }

    @PatchMapping("/{courierId}/status")
    @Operation(summary = "Suspend, deactivate, or reactivate a courier",
            description = "Suspending or deactivating revokes the courier's sessions immediately.")
    public ApiResponse<UserProfileResponse> updateStatus(@PathVariable UUID courierId,
                                                         @Valid @RequestBody UpdateUserStatusRequest request,
                                                         Authentication authentication) {
        return ApiResponse.success(courierAccountService.updateStatus(SecurityPrincipal.userId(authentication),
                courierId, request.status()), "Courier status updated");
    }
}
