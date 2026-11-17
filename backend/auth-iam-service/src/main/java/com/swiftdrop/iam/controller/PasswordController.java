package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.iam.dto.ChangePasswordRequest;
import com.swiftdrop.iam.dto.IdentifierRequest;
import com.swiftdrop.iam.dto.ResetPasswordRequest;
import com.swiftdrop.iam.security.SecurityPrincipal;
import com.swiftdrop.iam.service.PasswordResetService;
import com.swiftdrop.iam.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.swiftdrop.iam.controller.ClientRequests.clientIp;

/** Forgotten-password reset by emailed code, and password change for a signed-in user. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication, token rotation, and session management")
public class PasswordController {

    private final PasswordResetService passwordResetService;
    private final UserAccountService userAccountService;

    public PasswordController(PasswordResetService passwordResetService, UserAccountService userAccountService) {
        this.passwordResetService = passwordResetService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Email a password reset code",
            description = "Always answers 200 so it cannot be used to discover which accounts exist.")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody IdentifierRequest request,
                                            HttpServletRequest httpRequest) {
        passwordResetService.request(request.identifier(), clientIp(httpRequest));
        return ApiResponse.success("If the account exists, a password reset code has been sent.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using the emailed reset code",
            description = "Revokes every existing session of the account.")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                           HttpServletRequest httpRequest) {
        passwordResetService.reset(request.identifier(), request.code(), request.newPassword(), clientIp(httpRequest));
        return ApiResponse.success("Password updated. Sign in with your new password.");
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the authenticated user's password and revoke all sessions")
    @SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            Authentication authentication) {
        userAccountService.changePassword(SecurityPrincipal.userId(authentication), request);
        return ApiResponse.success("Password changed. Sign in again on all devices.");
    }
}
