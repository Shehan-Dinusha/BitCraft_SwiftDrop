package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.events.auth.OtpPurpose;
import com.swiftdrop.iam.config.OtpProperties;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.MfaChallengeResponse;
import com.swiftdrop.iam.exception.AccountNotActiveException;
import com.swiftdrop.iam.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Second sign-in factor (email one-time code) for privileged roles.
 *
 * <p>Why: a stolen password for a {@code SYSTEM_ADMIN} or {@code DISPATCHER} exposes every account
 * or a whole district, so those roles prove a second factor ("something you have": the mailbox)
 * before any token is issued. It reuses the existing OTP pipeline (Redis-backed codes, attempt
 * limits, notification-service email).
 *
 * <p>Flow: {@code /login} checks the password and, for these roles, returns a single-use
 * {@code challengeId} instead of tokens; {@code /login/mfa} exchanges challenge + code for tokens.
 * Only a SHA-256 hash of the challenge id is stored, so a Redis dump cannot complete a sign-in.
 *
 * <p>Off by default ({@code AUTH_MFA_ENABLED=false}) because it needs a real email provider.
 */
@Slf4j
@Service
public class LoginMfaService {

    static final Set<UserRole> PRIVILEGED_ROLES = EnumSet.of(UserRole.SYSTEM_ADMIN, UserRole.DISPATCHER);
    private static final String CHALLENGE_PREFIX = "auth:mfa:challenge:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean enabled;
    private final StringRedisTemplate redis;
    private final OtpService otpService;
    private final OtpProperties otpProperties;
    private final TokenIssuanceService tokenIssuanceService;
    private final UserAccountRepository userAccountRepository;

    public LoginMfaService(@Value("${swiftdrop.auth.mfa.enabled:false}") boolean enabled,
                           StringRedisTemplate redis,
                           OtpService otpService,
                           OtpProperties otpProperties,
                           TokenIssuanceService tokenIssuanceService,
                           UserAccountRepository userAccountRepository) {
        this.enabled = enabled;
        this.redis = redis;
        this.otpService = otpService;
        this.otpProperties = otpProperties;
        this.tokenIssuanceService = tokenIssuanceService;
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * @param user an account whose password was just verified
     * @return true when this account must complete a second factor before tokens are issued
     */
    public boolean isRequiredFor(UserAccount user) {
        return enabled && PRIVILEGED_ROLES.contains(user.getRole());
    }

    /**
     * Emails a code and opens a challenge for the user.
     *
     * @param user an account whose password was just verified
     * @return the challenge the client sends back with the code
     * @throws AuthException 429 if a code was requested moments ago; 503 if Redis is unavailable
     */
    public MfaChallengeResponse start(UserAccount user) {
        otpService.issue(OtpPurpose.LOGIN_MFA, user.getPublicId());
        String challengeId = newChallengeId();
        try {
            redis.opsForValue().set(challengeKey(challengeId), user.getPublicId().toString(),
                    Duration.ofMinutes(otpProperties.getTtlMinutes()));
        } catch (DataAccessException ex) {
            log.error("Could not store sign-in challenge: {}", ex.getMessage());
            throw unavailable();
        }
        return new MfaChallengeResponse(true, challengeId, otpProperties.getTtlMinutes());
    }

    /**
     * Verifies the emailed code for a challenge and closes the challenge.
     *
     * @param challengeId the id returned by {@link #start}
     * @param code        the 6-digit code from the email
     * @return the signed-in account, ready for token issuance
     * @throws AuthException 401 for an unknown/expired challenge, 400 for a wrong code,
     *                       429 after too many wrong codes (the challenge is then closed)
     */
    @Transactional(readOnly = true)
    public UserAccount complete(String challengeId, String code) {
        String key = challengeKey(challengeId);
        String userId;
        try {
            userId = redis.opsForValue().get(key);
        } catch (DataAccessException ex) {
            log.error("Could not read sign-in challenge: {}", ex.getMessage());
            throw unavailable();
        }
        if (userId == null) {
            throw expired();
        }
        UUID publicId = UUID.fromString(userId);
        try {
            otpService.verify(OtpPurpose.LOGIN_MFA, publicId, code);
        } catch (AuthException ex) {
            if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                deleteQuietly(key); // the code is gone; force a fresh password sign-in
            }
            throw ex;
        }
        deleteQuietly(key);

        UserAccount user = userAccountRepository.findByPublicId(publicId).orElseThrow(LoginMfaService::expired);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }
        return user;
    }

    private String challengeKey(String challengeId) {
        return CHALLENGE_PREFIX + tokenIssuanceService.hashToken(challengeId);
    }

    private void deleteQuietly(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException ex) {
            // The key expires on its own; the code itself is already consumed.
            log.warn("Could not delete sign-in challenge: {}", ex.getMessage());
        }
    }

    private static String newChallengeId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static AuthException expired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Your sign-in session expired. Please sign in again.");
    }

    private static AuthException unavailable() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                "Sign-in is temporarily unavailable. Please try again shortly.");
    }
}
