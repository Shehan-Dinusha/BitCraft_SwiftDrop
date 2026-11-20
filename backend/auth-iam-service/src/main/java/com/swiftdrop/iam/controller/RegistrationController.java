package com.swiftdrop.iam.controller;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.iam.dto.IdentifierRequest;
import com.swiftdrop.iam.dto.RegisterUserRequest;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.dto.UsernameAvailabilityResponse;
import com.swiftdrop.iam.dto.VerifyEmailRequest;
import com.swiftdrop.iam.service.EmailVerificationService;
import com.swiftdrop.iam.service.RegistrationService;
import com.swiftdrop.iam.service.UsernameAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.swiftdrop.iam.controller.ClientRequests.clientIp;

/** Self-service sign-up and email verification. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication, token rotation, and session management")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final UsernameAvailabilityService usernameAvailabilityService;
    private final EmailVerificationService emailVerificationService;

    public RegistrationController(RegistrationService registrationService,
                                  UsernameAvailabilityService usernameAvailabilityService,
                                  EmailVerificationService emailVerificationService) {
        this.registrationService = registrationService;
        this.usernameAvailabilityService = usernameAvailabilityService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a customer or merchant account",
            description = "Creates the account unverified and emails a 6-digit code. "
                    + "The user must confirm it via /verify-email before signing in.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(registrationService.register(request),
                        "Account created. Enter the verification code sent to your email."));
    }

    @GetMapping("/username-availability")
    @Operation(summary = "Check whether a username is free for a new account",
            description = "Used by the sign-up form while the user types. Case-insensitive; a pending "
                    + "(unverified) sign-up also holds its username. Rate limited per client IP. The "
                    + "database unique index remains the authority.")
    public ApiResponse<UsernameAvailabilityResponse> usernameAvailability(@RequestParam String username,
                                                                          HttpServletRequest httpRequest) {
        UsernameAvailabilityResponse result = usernameAvailabilityService.check(username, clientIp(httpRequest));
        return ApiResponse.success(result, result.available() ? "Username is available" : "Username is not available");
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address with the emailed code",
            description = "Marks the account verified. No session is created: the user signs in with /login "
                    + "afterwards. After 5 wrong codes the code is destroyed and a new one must be requested.")
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
}
