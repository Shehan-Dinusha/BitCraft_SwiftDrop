package com.swiftdrop.iam.controller;

import com.swiftdrop.iam.service.AvatarService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.PagedResult;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.iam.dto.CreateUserRequest;
import com.swiftdrop.iam.dto.DeleteAccountRequest;
import com.swiftdrop.iam.dto.UpdateUserRoleRequest;
import com.swiftdrop.iam.dto.UpdateUserStatusRequest;
import com.swiftdrop.iam.dto.UpdateProfileRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final AvatarService avatarService;

    public UserRoleController(UserAccountService userAccountService, AvatarService avatarService) {
        this.userAccountService = userAccountService;
        this.avatarService = avatarService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public ApiResponse<UserProfileResponse> currentUser(Authentication authentication) {
        return ApiResponse.success(
                userAccountService.getProfile(SecurityPrincipal.userId(authentication)),
                "Profile retrieved"
        );
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the authenticated user's own profile",
            description = "Changes first name, last name, phone and district. Username, email, role and status cannot be changed here.")
    public ApiResponse<UserProfileResponse> updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(
                userAccountService.updateOwnProfile(SecurityPrincipal.userId(authentication), request),
                "Profile updated"
        );
    }

    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Set the authenticated user's profile picture",
            description = "PNG, JPEG or WebP up to 512 KB, sent as the multipart part 'file'. The type is checked from the file content.")
    public ApiResponse<UserProfileResponse> uploadAvatar(@RequestPart("file") MultipartFile file,
                                                         Authentication authentication) throws IOException {
        return ApiResponse.success(
                avatarService.replace(SecurityPrincipal.userId(authentication), file.getBytes()),
                "Profile picture updated"
        );
    }

    @DeleteMapping("/me/avatar")
    @Operation(summary = "Remove the authenticated user's profile picture")
    public ApiResponse<UserProfileResponse> deleteAvatar(Authentication authentication) {
        return ApiResponse.success(avatarService.remove(SecurityPrincipal.userId(authentication)),
                "Profile picture removed");
    }

    @GetMapping("/me/avatar")
    @Operation(summary = "Get the authenticated user's profile picture", description = "404 when there is none.")
    public ResponseEntity<byte[]> getAvatar(Authentication authentication) {
        return avatarService.find(SecurityPrincipal.userId(authentication))
                .map(avatar -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(avatar.contentType()))
                        .cacheControl(CacheControl.noCache().cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(avatar.image()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Erase the authenticated customer's own account",
            description = "Needs the current password. Removes the username, email, phone, names and "
                    + "district, revokes every session and blocks sign-in. Orders already placed keep "
                    + "an owner id but no personal data.")
    public ApiResponse<Void> deleteOwnAccount(@Valid @RequestBody DeleteAccountRequest request,
                                              Authentication authentication) {
        userAccountService.deleteOwnAccount(SecurityPrincipal.userId(authentication), request);
        return ApiResponse.success("Your account has been deleted.");
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
    @Operation(summary = "Verify an existing account role", description = "Roles are fixed at account creation. Requests for a different role return 409; the current role is an idempotent no-op.")
    public ApiResponse<UserProfileResponse> updateRole(@PathVariable UUID userId,
                                                       @Valid @RequestBody UpdateUserRoleRequest request) {
        return ApiResponse.success(userAccountService.updateRole(userId, request.role()), "Account role unchanged");
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Suspend, deactivate, or reactivate a user")
    public ApiResponse<UserProfileResponse> updateStatus(@PathVariable UUID userId,
                                                         @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(userAccountService.updateStatus(userId, request.status()), "User status updated");
    }
}
