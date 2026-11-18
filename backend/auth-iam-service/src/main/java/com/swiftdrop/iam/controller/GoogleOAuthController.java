package com.swiftdrop.iam.controller;

import com.swiftdrop.iam.dto.OAuthExchangeRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.exception.AuthException;
import com.swiftdrop.iam.security.RefreshTokenCookies;
import com.swiftdrop.iam.service.GoogleOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

import static com.swiftdrop.iam.controller.ClientRequests.clientIp;
import static com.swiftdrop.iam.controller.ClientRequests.userAgent;

/**
 * "Sign in with Google" (OAuth 2.0 authorization-code flow). The Google code is exchanged on the
 * server, so the client secret never reaches the browser, and tokens never appear in a URL.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication, token rotation, and session management")
public class GoogleOAuthController {

    private final GoogleOAuthService googleOAuthService;
    private final RefreshTokenCookies refreshTokenCookies;

    public GoogleOAuthController(GoogleOAuthService googleOAuthService, RefreshTokenCookies refreshTokenCookies) {
        this.googleOAuthService = googleOAuthService;
        this.refreshTokenCookies = refreshTokenCookies;
    }

    /** Generates a CSRF state token, stores it in Redis and redirects to Google's consent screen. */
    @GetMapping("/oauth2/google/authorize")
    @Operation(summary = "Start Google OAuth 2.0 login", description = "Redirects the browser to Google's "
            + "consent screen. Returns 503 if Google OAuth credentials are not configured.")
    public ResponseEntity<Void> googleAuthorize() {
        URI redirectUri = googleOAuthService.buildAuthorizeRedirectUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
    }

    /**
     * Validates the state, exchanges Google's code and upserts the user, then redirects the
     * frontend to {@code /auth/callback?code=<one-time exchange code>}.
     */
    @GetMapping("/oauth2/google/callback")
    @Operation(summary = "Google OAuth 2.0 callback", description = "Receives the authorization code from "
            + "Google, validates the state token, upserts the user, and redirects to the frontend callback "
            + "page with a short-lived one-time exchange code in the query string.")
    public ResponseEntity<Void> googleCallback(@RequestParam(required = false) String code,
                                               @RequestParam(required = false) String state,
                                               @RequestParam(required = false) String error,
                                               HttpServletRequest httpRequest) {
        if (error != null) {
            // User denied consent or Google returned an error.
            return redirect(googleOAuthService.buildFrontendErrorRedirect(error));
        }

        GoogleOAuthService.CallbackResult result;
        try {
            result = googleOAuthService.handleCallback(code, state, userAgent(httpRequest));
        } catch (AuthException ex) {
            // Browser navigation: send the user back to the app, not a raw JSON error.
            log.warn("Google OAuth callback failed: {}", ex.getMessage());
            return redirect(googleOAuthService.buildFrontendErrorRedirect(oauthErrorCode(ex)));
        }

        // A brand-new Google account sees the "registered" page and then signs in; an existing
        // account is signed straight in via the one-time exchange code.
        return redirect(result.newAccount()
                ? googleOAuthService.buildFrontendRegisteredRedirect()
                : googleOAuthService.buildFrontendRedirect(result.exchangeCode()));
    }

    /** Swaps the single-use exchange code (valid 2 minutes) for a token pair. */
    @PostMapping("/oauth2/exchange")
    @Operation(summary = "Exchange OAuth one-time code for SwiftDrop tokens", description = "Accepts the "
            + "short-lived code issued by the Google callback and returns a standard TokenResponse. The code "
            + "is immediately consumed and cannot be reused.")
    public ResponseEntity<TokenResponse> googleExchange(@Valid @RequestBody OAuthExchangeRequest request,
                                                        HttpServletRequest httpRequest) {
        return refreshTokenCookies.issue(
                googleOAuthService.exchangeCode(request.code(), clientIp(httpRequest), userAgent(httpRequest)));
    }

    private static ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

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
}
