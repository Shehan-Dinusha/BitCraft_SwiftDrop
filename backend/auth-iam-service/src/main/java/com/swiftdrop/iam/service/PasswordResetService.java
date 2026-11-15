package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.events.auth.OtpPurpose;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * "Forgot password": emails a one-time code, then accepts that code together with a new password.
 *
 * <p>Redeeming the code proves control of the mailbox, so an account that was still unverified is
 * marked verified. Every existing session is revoked so a stolen password cannot outlive the reset.
 */
@Slf4j
@Service
public class PasswordResetService {

    private final UserAccountRepository userAccountRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RefreshTokenService refreshTokenService;
    private final TokenIssuanceService tokenIssuanceService;

    public PasswordResetService(UserAccountRepository userAccountRepository,
                                OtpService otpService,
                                PasswordEncoder passwordEncoder,
                                PasswordPolicy passwordPolicy,
                                RefreshTokenService refreshTokenService,
                                TokenIssuanceService tokenIssuanceService) {
        this.userAccountRepository = userAccountRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshTokenService = refreshTokenService;
        this.tokenIssuanceService = tokenIssuanceService;
    }

    /**
     * Starts a reset. Always completes silently so the response never reveals whether the
     * identifier belongs to an account.
     */
    @Transactional(readOnly = true)
    public void request(String identifier, String ipAddress) {
        otpService.assertIpAllowed(ipAddress);
        findUser(identifier)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    try {
                        otpService.issue(OtpPurpose.PASSWORD_RESET, user.getPublicId());
                    } catch (AuthException ex) {
                        if (ex.getStatus() != HttpStatus.TOO_MANY_REQUESTS) {
                            throw ex;
                        }
                        log.debug("Password reset request throttled for user {}", user.getPublicId());
                    }
                });
    }

    @Transactional
    public void reset(String identifier, String code, String newPassword, String ipAddress) {
        otpService.assertIpAllowed(ipAddress);

        UserAccount user = findUser(identifier)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(OtpService::invalidCode);

        // 1. Generic rules first: they reveal nothing about the account.
        // 2. Then the code. Only a caller holding a valid code gets account-specific feedback (contains
        //    your username/email, same as the current password), so this endpoint can be used neither
        //    to probe someone's email address nor to guess their password.
        // 3. The code is spent only once the reset is certain to succeed, so a rejected password does
        //    not force the user to request a new code.
        passwordPolicy.validate(newPassword);
        otpService.check(OtpPurpose.PASSWORD_RESET, user.getPublicId(), code);
        passwordPolicy.validate(newPassword, user.getUsername(), user.getEmail());
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from your current password.");
        }
        otpService.consume(OtpPurpose.PASSWORD_RESET, user.getPublicId());

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        userAccountRepository.save(user);

        refreshTokenService.revokeAll(user);
        tokenIssuanceService.blacklistUser(user.getPublicId());
    }

    private Optional<UserAccount> findUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim();
        return userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(trimmed, trimmed);
    }
}
