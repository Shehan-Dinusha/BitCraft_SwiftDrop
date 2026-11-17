package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.iam.dto.ChangePasswordRequest;
import com.swiftdrop.iam.dto.IdentifierRequest;
import com.swiftdrop.iam.exception.AuthException;
import com.swiftdrop.iam.dto.LoginRequest;
import com.swiftdrop.iam.dto.LogoutRequest;
import com.swiftdrop.iam.dto.OAuthExchangeRequest;
import com.swiftdrop.iam.dto.RefreshTokenRequest;
import com.swiftdrop.iam.dto.RegisterUserRequest;
import com.swiftdrop.iam.dto.ResetPasswordRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.dto.UsernameAvailabilityResponse;
import com.swiftdrop.iam.dto.VerifyEmailRequest;
import com.swiftdrop.iam.security.RefreshTokenCookies;
import com.swiftdrop.iam.security.SecurityPrincipal;
import com.swiftdrop.iam.service.AuthenticationService;
import com.swiftdrop.iam.service.EmailVerificationService;
import com.swiftdrop.iam.service.GoogleOAuthService;
import com.swiftdrop.iam.service.PasswordResetService;
import com.swiftdrop.iam.service.RegistrationService;
import com.swiftdrop.iam.service.UserAccountService;
import com.swiftdrop.iam.service.UsernameAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
@Slf4j
public class AuthController {

        private final AuthenticationService authenticationService;
        private final UserAccountService userAccountService;
        private final GoogleOAuthService googleOAuthService;
        private final RegistrationService registrationService;
        private final EmailVerificationService emailVerificationService;
        private final PasswordResetService passwordResetService;
        private final UsernameAvailabilityService usernameAvailabilityService;
        private final RefreshTokenCookies refreshTokenCookies;

        public AuthController(AuthenticationService authenticationService,
                        UserAccountService userAccountService,
                        GoogleOAuthService googleOAuthService,
                        RegistrationService registrationService,
                        EmailVerificationService emailVerificationService,
                        PasswordResetService passwordResetService,
                        UsernameAvailabilityService usernameAvailabilityService,
                        RefreshTokenCookies refreshTokenCookies) {
                this.refreshTokenCookies = refreshTokenCookies;
                this.usernameAvailabilityService = usernameAvailabilityService;
                this.authenticationService = authenticationService;
                this.userAccountService = userAccountService;
                this.googleOAuthService = googleOAuthService;
                this.registrationService = registrationService;
                this.emailVerificationService = emailVerificationService;
                this.passwordResetService = passwordResetService;
        }

        // ─── Password-based auth ────────────────────────────────────────────────────

        @PostMapping("/login")
        @Operation(summary = "Authenticate and issue access and refresh tokens",
                        description = "The refresh token is set as the HttpOnly swiftdrop_refresh cookie "
                                        + "and is not included in the response body.")
        public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                        HttpServletRequest httpRequest) {
                return refreshTokenCookies.issue(authenticationService.login(
                                request.username(),
                                request.password(),
                                clientIp(httpRequest),
                                httpRequest.getHeader("User-Agent")));
        }

        @PostMapping("/register")
        @Operation(summary = "Register a customer or merchant account",
                        description = "Creates the account unverified and emails a 6-digit code. "
                                        + "The user must confirm it via /verify-email before signing in.")
        public ResponseEntity<ApiResponse<UserProfileResponse>> register(
                        @Valid @RequestBody RegisterUserRequest request) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(registrationService.register(request),
                                                "Account created. Enter the verification code sent to your email."));
        }

        @GetMapping("/username-availability")
        @Operation(summary = "Check whether a username is free for a new account",
                        description = "Used by the sign-up form while the user types. Case-insensitive; a "
                                        + "pending (unverified) sign-up also holds its username. Rate limited "
                                        + "per client IP. The database unique index remains the authority.")
        public ApiResponse<UsernameAvailabilityResponse> usernameAvailability(
                        @RequestParam String username,
                        HttpServletRequest httpRequest) {
                UsernameAvailabilityResponse result = usernameAvailabilityService.check(username, clientIp(httpRequest));
                return ApiResponse.success(result,
                                result.available() ? "Username is available" : "Username is not available");
        }

        // ─── Email verification and password reset (one-time codes) ───────────────────

        @PostMapping("/verify-email")
        @Operation(summary = "Confirm an email address with the emailed code",
                        description = "Marks the account verified. No session is created: the user signs in "
                                        + "with /login afterwards. After 5 wrong codes the code is destroyed "
                                        + "and a new one must be requested.")
        public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request,
                        HttpServletRequest httpRequest) {
                emailVerificationService.verify(request.identifier(), request.code(), clientIp(httpRequest));
                return ApiResponse.success("Email verified. You can now sign in.");
        }

        @PostMapping("/resend-verification")
        @Operation(summary = "Send a new email verification code",
                        description = "Always answers 200 so it cannot be used to discover which accounts exist.")
        public ApiResponse<Void> resendVerification(@Valid @RequestBody IdentifierRequest request,
                        HttpServletRequest httpRequest) {
                emailVerificationService.resend(request.identifier(), clientIp(httpRequest));
                return ApiResponse.success("If the account exists and is not verified yet, a new code has been sent.");
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
                passwordResetService.reset(request.identifier(), request.code(), request.newPassword(),
                                clientIp(httpRequest));
                return ApiResponse.success("Password updated. Sign in with your new password.");
        }

        @PostMapping("/refresh")
        @Operation(summary = "Rotate a refresh token and issue a new access token",
                        description = "Reads the refresh token from the swiftdrop_refresh cookie, or from the "
                                        + "body for non-browser clients. The rotated token is set as a new cookie.")
        public ResponseEntity<TokenResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request,
                        HttpServletRequest httpRequest) {
                String refreshToken = refreshTokenCookies.resolve(
                                request == null ? null : request.refreshToken(), httpRequest);
                return refreshTokenCookies.issue(authenticationService.refresh(
                                refreshToken,
                                clientIp(httpRequest),
                                httpRequest.getHeader("User-Agent")));
        }

        @PostMapping("/logout")
        @Operation(summary = "Revoke the current refresh token and access token")
        @SecurityRequirement(name = OpenApiConstants.BEARER_AUTH_SCHEME)
        public ResponseEntity<ApiResponse<Void>> logout(
                        @RequestBody(required = false) LogoutRequest request,
                        @RequestHeader(value = JwtHeaderConstants.AUTHORIZATION, required = false) String authorization,
                        HttpServletRequest httpRequest) {
                authenticationService.logout(
                                refreshTokenCookies.resolve(request == null ? null : request.refreshToken(), httpRequest),
                                bearerToken(authorization));
                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clearHeader())
                                .body(ApiResponse.success("Logged out"));
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
         * Generates a CSRF state token, stores it in Redis, and redirects the browser
         * to
         * Google's consent screen. The registered redirect URI must point back to
         * {@link #googleCallback}.
         */
        @GetMapping("/oauth2/google/authorize")
        @Operation(summary = "Start Google OAuth 2.0 login", description = "Redirects the browser to Google's consent screen. "
                        + "Returns 503 if Google OAuth credentials are not configured.")
        public ResponseEntity<Void> googleAuthorize() {
                URI redirectUri = googleOAuthService.buildAuthorizeRedirectUri();
                return ResponseEntity.status(HttpStatus.FOUND)
                                .location(redirectUri)
                                .build();
        }

        /**
         * Stage 13 – Google callback: validate state, exchange code, upsert user.
         * Issues a one-time exchange code and redirects the frontend to
         * {@code /auth/callback?code=<exchange-code>}. The frontend must immediately
         * call
         * {@link #googleExchange} to swap it for tokens. Tokens are never placed in the
         * URL.
         */
        @GetMapping("/oauth2/google/callback")
        @Operation(summary = "Google OAuth 2.0 callback", description = "Receives the authorization code from Google, validates the state "
                        + "token, upserts the user, and redirects to the frontend callback page "
                        + "with a short-lived one-time exchange code in the query string.")
        public ResponseEntity<Void> googleCallback(
                        @RequestParam(required = false) String code,
                        @RequestParam(required = false) String state,
                        @RequestParam(required = false) String error,
                        HttpServletRequest httpRequest) {

                if (error != null) {
                        // User denied consent or Google returned an error.
                        return ResponseEntity.status(HttpStatus.FOUND)
                                        .location(googleOAuthService.buildFrontendErrorRedirect(error))
                                        .build();
                }

                GoogleOAuthService.CallbackResult result;
                try {
                        result = googleOAuthService.handleCallback(
                                        code, state, httpRequest.getHeader("User-Agent"));
                } catch (AuthException ex) {
                        // Browser navigation: send the user back to the app, not a raw JSON error.
                        log.warn("Google OAuth callback failed: {}", ex.getMessage());
                        return ResponseEntity.status(HttpStatus.FOUND)
                                        .location(googleOAuthService.buildFrontendErrorRedirect(oauthErrorCode(ex)))
                                        .build();
                }

                // Redirect to the frontend callback page; the page calls /oauth2/exchange.
                // Tokens are NOT embedded in the URL.
                // A brand-new Google account is shown the "registered" page and then signs in;
                // an existing account is signed straight in via the one-time exchange code.
                URI frontendRedirect = result.newAccount()
                                ? googleOAuthService.buildFrontendRegisteredRedirect()
                                : googleOAuthService.buildFrontendRedirect(result.exchangeCode());
                return ResponseEntity.status(HttpStatus.FOUND)
                                .location(frontendRedirect)
                                .build();
        }

        /**
         * Stage 13 – Token exchange: swap the one-time code for a full token pair.
         * The exchange code is single-use and expires in 2 minutes.
         */
        @PostMapping("/oauth2/exchange")
        @Operation(summary = "Exchange OAuth one-time code for SwiftDrop tokens", description = "Accepts the short-lived code issued by the Google callback and "
                        + "returns a standard TokenResponse. The code is immediately consumed "
                        + "and cannot be reused.")
        public ResponseEntity<TokenResponse> googleExchange(
                        @Valid @RequestBody OAuthExchangeRequest request,
                        HttpServletRequest httpRequest) {
                return refreshTokenCookies.issue(googleOAuthService.exchangeCode(
                                request.code(),
                                clientIp(httpRequest),
                                httpRequest.getHeader("User-Agent")));
        }

        // ─── Helpers ────────────────────────────────────────────────────────────────

        private static String oauthErrorCode(AuthException ex) {
                if ("email_unverified".equals(ex.getCode())) {
                        return "email_unverified";
                }
                return switch (ex.getStatus()) {
                        case SERVICE_UNAVAILABLE -> "not_configured";
                        case FORBIDDEN -> "account_inactive";
                        case BAD_REQUEST -> "invalid_state";
                        default -> "google_unavailable";
                };
        }

        private String clientIp(HttpServletRequest request) {
                // The gateway appends the address it received the request from, so the rightmost entry
                // is the trustworthy one; earlier entries are whatever the client chose to send.
                String forwardedFor = request.getHeader("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.isBlank()) {
                        String[] hops = forwardedFor.split(",");
                        return hops[hops.length - 1].trim();
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
