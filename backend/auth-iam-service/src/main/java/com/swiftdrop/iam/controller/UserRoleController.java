package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.iam.dto.CreateUserRequest;
import com.swiftdrop.iam.dto.UpdateUserRoleRequest;
import com.swiftdrop.iam.dto.UpdateUserStatusRequest;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.security.SecurityPrincipal;
import com.swiftdrop.iam.service.UserAccountService;
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
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile and administrative identity management")
@SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
public class UserRoleController {

    private final UserAccountService userAccountService;

    public UserRoleController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public ApiResponse<UserProfileResponse> currentUser(Authentication authentication) {
        return ApiResponse.success(
                userAccountService.getProfile(SecurityPrincipal.userId(authentication)),
                "Profile retrieved"
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "List and search users")
    public ApiResponse<PagedResult<UserProfileResponse>> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(userAccountService.list(role, status, search, page, size), "Users retrieved");
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get a user by public identifier")
    public ApiResponse<UserProfileResponse> getUser(@PathVariable UUID userId) {
        return ApiResponse.success(userAccountService.getProfile(userId), "User retrieved");
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Provision a platform account with any role")
    public ResponseEntity<ApiResponse<UserProfileResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userAccountService.create(request), "User created"));
    }

    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Change a user's role")
    public ApiResponse<UserProfileResponse> updateRole(@PathVariable UUID userId,
                                                       @Valid @RequestBody UpdateUserRoleRequest request) {
        return ApiResponse.success(userAccountService.updateRole(userId, request.role()), "User role updated");
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Suspend, deactivate, or reactivate a user")
    public ApiResponse<UserProfileResponse> updateStatus(@PathVariable UUID userId,
                                                         @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(userAccountService.updateStatus(userId, request.status()), "User status updated");
    }
}
