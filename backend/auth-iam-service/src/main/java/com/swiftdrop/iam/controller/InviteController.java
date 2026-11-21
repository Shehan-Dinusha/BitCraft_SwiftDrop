package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.dto.AcceptInviteRequest;
import com.swiftdrop.iam.dto.InvitePreviewResponse;
import com.swiftdrop.iam.dto.InviteTokenRequest;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.security.RefreshTokenCookies;
import com.swiftdrop.iam.service.AuthenticationService;
import com.swiftdrop.iam.service.OtpService;
import com.swiftdrop.iam.service.StaffInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.swiftdrop.iam.controller.ClientRequests.clientIp;
import static com.swiftdrop.iam.controller.ClientRequests.userAgent;

/**
 * Public endpoints behind the staff invite link. The token is sent in the request body (never the
 * URL), and requests count against the same per-IP limit as the one-time-code endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth/invite")
@Tag(name = "Staff invites", description = "Couriers and dispatchers accept their email invite and choose a password")
public class InviteController {

    private final StaffInviteService staffInviteService;
    private final AuthenticationService authenticationService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final OtpService otpService;

    public InviteController(StaffInviteService staffInviteService,
                            AuthenticationService authenticationService,
                            RefreshTokenCookies refreshTokenCookies,
                            OtpService otpService) {
        this.staffInviteService = staffInviteService;
        this.authenticationService = authenticationService;
        this.refreshTokenCookies = refreshTokenCookies;
        this.otpService = otpService;
    }

    @PostMapping("/preview")
    @Operation(summary = "Check an invite link",
            description = "Returns who the invite is for. 400 when the link is invalid, used or expired.")
    public ApiResponse<InvitePreviewResponse> preview(@Valid @RequestBody InviteTokenRequest request,
                                                      HttpServletRequest httpRequest) {
        otpService.assertIpAllowed(clientIp(httpRequest));
        return ApiResponse.success(staffInviteService.preview(request.token()), "Invite is valid");
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept an invite and sign in",
            description = "Sets the password, spends the invite and signs the person in. The refresh token is set "
                    + "as the HttpOnly swiftdrop_refresh cookie.")
    public ResponseEntity<TokenResponse> accept(@Valid @RequestBody AcceptInviteRequest request,
                                                HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        otpService.assertIpAllowed(ip);
        UserAccount user = staffInviteService.accept(request.token(), request.password());
        return refreshTokenCookies.issue(authenticationService.issueSession(user, ip, userAgent(httpRequest)));
    }
}
