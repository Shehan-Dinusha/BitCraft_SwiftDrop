package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.iam.dto.ChangePasswordRequest;
import com.swiftdrop.iam.dto.LoginRequest;
import com.swiftdrop.iam.dto.LogoutRequest;
import com.swiftdrop.iam.dto.RefreshTokenRequest;
import com.swiftdrop.iam.dto.RegisterUserRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.security.SecurityPrincipal;
import com.swiftdrop.iam.service.AuthenticationService;
import com.swiftdrop.iam.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication, token rotation, and session management")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserAccountService userAccountService;

    public AuthController(AuthenticationService authenticationService, UserAccountService userAccountService) {
        this.authenticationService = authenticationService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and issue access and refresh tokens")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authenticationService.login(
                request.username(),
                request.password(),
                clientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
    }

    @PostMapping("/register")
    @Operation(summary = "Register a customer or merchant account")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userAccountService.register(request), "Account registered"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new access token")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        return authenticationService.refresh(
                request.refreshToken(),
                clientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh token and access token")
    @SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequest request,
                                    @RequestHeader(value = JwtHeaderConstants.AUTHORIZATION, required = false)
                                    String authorization) {
        authenticationService.logout(
                request == null ? null : request.refreshToken(),
                bearerToken(authorization)
        );
        return ApiResponse.success("Logged out");
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the authenticated user's password and revoke all sessions")
    @SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            Authentication authentication) {
        userAccountService.changePassword(SecurityPrincipal.userId(authentication), request);
        return ApiResponse.success("Password changed. Sign in again on all devices.");
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(JwtHeaderConstants.BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(JwtHeaderConstants.BEARER_PREFIX.length()).trim();
    }
}
