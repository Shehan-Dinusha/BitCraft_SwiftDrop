package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.iam.dto.ChangePasswordRequest;
import com.swiftdrop.iam.dto.LoginRequest;
import com.swiftdrop.iam.dto.LogoutRequest;
import com.swiftdrop.iam.dto.OAuthExchangeRequest;
import com.swiftdrop.iam.dto.RefreshTokenRequest;
import com.swiftdrop.iam.dto.RegisterUserRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.security.SecurityPrincipal;
import com.swiftdrop.iam.service.AuthenticationService;
import com.swiftdrop.iam.service.GoogleOAuthService;
import com.swiftdrop.iam.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication, token rotation, and session management")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserAccountService userAccountService;
    private final GoogleOAuthService googleOAuthService;

    public AuthController(AuthenticationService authenticationService,
                          UserAccountService userAccountService,
                          GoogleOAuthService googleOAuthService) {
        this.authenticationService = authenticationService;
        this.userAccountService = userAccountService;
        this.googleOAuthService = googleOAuthService;
    }

    // ─── Password-based auth ────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Authenticate and issue access and refresh tokens")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {
        return authenticationService.login(
                request.username(),
                request.password(),
                clientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
    }

    @PostMapping("/register")
    @Operation(summary = "Register a customer or merchant account")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(
            @Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userAccountService.register(request), "Account registered"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new access token")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request,
                                 HttpServletRequest httpRequest) {
        return authenticationService.refresh(
                request.refreshToken(),
                clientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh token and access token")
    @SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
    public ApiResponse<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
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
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userAccountService.changePassword(SecurityPrincipal.userId(authentication), request);
        return ApiResponse.success("Password changed. Sign in again on all devices.");
    }

    // ─── Google OAuth 2.0 ───────────────────────────────────────────────────────

    /**
     * Stage 12 – Start the Google login flow.
     * Generates a CSRF state token, stores it in Redis, and redirects the browser to
     * Google's consent screen. The registered redirect URI must point back to
     * {@link #googleCallback}.
     */
    @GetMapping("/oauth2/google/authorize")
    @Operation(
            summary = "Start Google OAuth 2.0 login",
            description = "Redirects the browser to Google's consent screen. "
                    + "Returns 503 if Google OAuth credentials are not configured."
    )
    public ResponseEntity<Void> googleAuthorize() {
        URI redirectUri = googleOAuthService.buildAuthorizeRedirectUri();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirectUri)
                .build();
    }

    /**
     * Stage 13 – Google callback: validate state, exchange code, upsert user.
     * Issues a one-time exchange code and redirects the frontend to
     * {@code /auth/callback?code=<exchange-code>}.  The frontend must immediately call
     * {@link #googleExchange} to swap it for tokens.  Tokens are never placed in the URL.
     */
    @GetMapping("/oauth2/google/callback")
    @Operation(
            summary = "Google OAuth 2.0 callback",
            description = "Receives the authorization code from Google, validates the state "
                    + "token, upserts the user, and redirects to the frontend callback page "
                    + "with a short-lived one-time exchange code in the query string."
    )
    public ResponseEntity<Void> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest httpRequest) {

        if (error != null) {
            // User denied consent or Google returned an error.
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("/login?oauth_error=access_denied"))
                    .build();
        }

        String exchangeCode = googleOAuthService.handleCallback(
                code, state, httpRequest.getHeader("User-Agent"));

        // Redirect to the frontend callback page; the page calls /oauth2/exchange.
        // Tokens are NOT embedded in the URL.
        URI frontendRedirect = URI.create("/auth/callback?code=" + exchangeCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(frontendRedirect)
                .build();
    }

    /**
     * Stage 13 – Token exchange: swap the one-time code for a full token pair.
     * The exchange code is single-use and expires in 2 minutes.
     */
    @PostMapping("/oauth2/exchange")
    @Operation(
            summary = "Exchange OAuth one-time code for SwiftDrop tokens",
            description = "Accepts the short-lived code issued by the Google callback and "
                    + "returns a standard TokenResponse. The code is immediately consumed "
                    + "and cannot be reused."
    )
    public TokenResponse googleExchange(
            @Valid @RequestBody OAuthExchangeRequest request,
            HttpServletRequest httpRequest) {
        return googleOAuthService.exchangeCode(
                request.code(),
                clientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

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
        if (authorization == null
                || !authorization.startsWith(JwtHeaderConstants.BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(JwtHeaderConstants.BEARER_PREFIX.length()).trim();
    }
}
