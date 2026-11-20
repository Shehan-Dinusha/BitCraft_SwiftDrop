package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.openapi.OpenApiConstants;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.dto.LoginMfaRequest;
import com.swiftdrop.iam.dto.LoginRequest;
import com.swiftdrop.iam.dto.LogoutRequest;
import com.swiftdrop.iam.dto.MfaChallengeResponse;
import com.swiftdrop.iam.dto.RefreshTokenRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.security.RefreshTokenCookies;
import com.swiftdrop.iam.service.AuthenticationService;
import com.swiftdrop.iam.service.LoginMfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.swiftdrop.iam.controller.ClientRequests.bearerToken;
import static com.swiftdrop.iam.controller.ClientRequests.clientIp;
import static com.swiftdrop.iam.controller.ClientRequests.userAgent;

/** Password sign-in (with the optional second factor), token rotation and logout. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication, token rotation, and session management")
public class SessionController {

    private final AuthenticationService authenticationService;
    private final LoginMfaService loginMfaService;
    private final RefreshTokenCookies refreshTokenCookies;

    public SessionController(AuthenticationService authenticationService,
                             LoginMfaService loginMfaService,
                             RefreshTokenCookies refreshTokenCookies) {
        this.authenticationService = authenticationService;
        this.loginMfaService = loginMfaService;
        this.refreshTokenCookies = refreshTokenCookies;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and issue access and refresh tokens",
            description = "The refresh token is set as the HttpOnly swiftdrop_refresh cookie and is not "
                    + "included in the response body. When the account needs a second factor "
                    + "(SYSTEM_ADMIN/DISPATCHER with AUTH_MFA_ENABLED) the answer is 202 with an "
                    + "MfaChallengeResponse; finish with /login/mfa.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Signed in",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Second factor required",
            content = @Content(schema = @Schema(implementation = MfaChallengeResponse.class)))
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        UserAccount user = authenticationService.authenticate(request.username(), request.password(), ip, userAgent(httpRequest));
        if (loginMfaService.isRequiredFor(user)) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(loginMfaService.start(user));
        }
        return refreshTokenCookies.issue(authenticationService.issueSession(user, ip, userAgent(httpRequest)));
    }

    @PostMapping("/login/mfa")
    @Operation(summary = "Complete a sign-in with the emailed second-factor code",
            description = "Exchanges the challenge from a 202 /login response and the emailed code for a "
                    + "token pair. After 5 wrong codes the user must sign in again.")
    public ResponseEntity<TokenResponse> loginMfa(@Valid @RequestBody LoginMfaRequest request,
                                                  HttpServletRequest httpRequest) {
        UserAccount user = loginMfaService.complete(request.challengeId(), request.code());
        return refreshTokenCookies.issue(
                authenticationService.issueSession(user, clientIp(httpRequest), userAgent(httpRequest)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new access token",
            description = "Reads the refresh token from the swiftdrop_refresh cookie, or from the body for "
                    + "non-browser clients. The rotated token is set as a new cookie.")
    public ResponseEntity<TokenResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request,
                                                 HttpServletRequest httpRequest) {
        String refreshToken = refreshTokenCookies.resolve(request == null ? null : request.refreshToken(), httpRequest);
        return refreshTokenCookies.issue(
                authenticationService.refresh(refreshToken, clientIp(httpRequest), userAgent(httpRequest)));
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
}
