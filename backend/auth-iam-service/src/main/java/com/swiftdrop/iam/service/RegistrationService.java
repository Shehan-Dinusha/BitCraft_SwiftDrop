package com.swiftdrop.iam.service;

import com.swiftdrop.iam.dto.RegisterUserRequest;
import com.swiftdrop.iam.dto.UserProfileResponse;
import com.swiftdrop.iam.exception.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Self-service sign-up: creates the (unverified) account, then emails the verification code.
 * Deliberately not transactional, so the account is committed before the code is issued.
 */
@Service
public class RegistrationService {

    private final UserAccountService userAccountService;
    private final EmailVerificationService emailVerificationService;

    public RegistrationService(UserAccountService userAccountService,
                               EmailVerificationService emailVerificationService) {
        this.userAccountService = userAccountService;
        this.emailVerificationService = emailVerificationService;
    }

    public UserProfileResponse register(RegisterUserRequest request) {
        UserProfileResponse profile = userAccountService.register(request);
        try {
            emailVerificationService.sendCode(profile.id());
        } catch (AuthException ex) {
            // A repeat sign-up inside the resend cooldown keeps the code that was already sent.
            if (ex.getStatus() != HttpStatus.TOO_MANY_REQUESTS) {
                throw ex;
            }
        }
        return profile;
    }
}
