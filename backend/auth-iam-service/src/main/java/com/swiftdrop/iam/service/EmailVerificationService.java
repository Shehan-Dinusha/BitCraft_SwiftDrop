package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.events.auth.OtpPurpose;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.exception.AccountNotActiveException;
import com.swiftdrop.iam.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Email-ownership check for self-service sign-ups. A new password account is created unverified;
 * the user proves control of the mailbox by entering the code we email. Verifying does NOT create a
 * session: the user is shown a "registered" confirmation and then signs in with their password.
 */
@Slf4j
@Service
public class EmailVerificationService {

    private final UserAccountRepository userAccountRepository;
    private final OtpService otpService;

    public EmailVerificationService(UserAccountRepository userAccountRepository, OtpService otpService) {
        this.userAccountRepository = userAccountRepository;
        this.otpService = otpService;
    }

    /** Sends the first code right after registration. */
    public void sendCode(UUID publicId) {
        otpService.issue(OtpPurpose.EMAIL_VERIFICATION, publicId);
    }

    /**
     * Sends a fresh code. Always completes silently so the response never reveals whether the
     * identifier belongs to an account, or whether that account still needs verifying.
     */
    @Transactional(readOnly = true)
    public void resend(String identifier, String ipAddress) {
        otpService.assertIpAllowed(ipAddress);
        findUser(identifier)
                .filter(user -> !user.isEmailVerified() && user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    try {
                        otpService.issue(OtpPurpose.EMAIL_VERIFICATION, user.getPublicId());
                    } catch (AuthException ex) {
                        if (ex.getStatus() != HttpStatus.TOO_MANY_REQUESTS) {
                            throw ex;
                        }
                        log.debug("Verification resend throttled for user {}", user.getPublicId());
                    }
                });
    }

    /** Confirms the code and marks the email verified. The user signs in separately afterwards. */
    @Transactional
    public void verify(String identifier, String code, String ipAddress) {
        otpService.assertIpAllowed(ipAddress);

        UserAccount user = findUser(identifier).orElseThrow(OtpService::invalidCode);
        if (user.isEmailVerified()) {
            throw new AuthException(HttpStatus.CONFLICT, "Email address is already verified. Please sign in.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        otpService.verify(OtpPurpose.EMAIL_VERIFICATION, user.getPublicId(), code);

        user.setEmailVerified(true);
        userAccountRepository.save(user);
    }

    private Optional<UserAccount> findUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        return userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(trimmed, trimmed);
    }
}
