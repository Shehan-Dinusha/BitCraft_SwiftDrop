package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.exception.AccountNotActiveException;
import com.swiftdrop.iam.exception.EmailNotVerifiedException;
import com.swiftdrop.iam.exception.InvalidCredentialsException;
import com.swiftdrop.iam.exception.InvalidRefreshTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthenticationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuanceService tokenIssuanceService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    /** bcrypt hash of a random value, used only to equalize login timing for unknown users. */
    private volatile String timingEqualizerHash;

    public AuthenticationService(UserAccountRepository userAccountRepository,
                                 PasswordEncoder passwordEncoder,
                                 TokenIssuanceService tokenIssuanceService,
                                 RefreshTokenService refreshTokenService,
                                 LoginAttemptService loginAttemptService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuanceService = tokenIssuanceService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional
    public TokenResponse login(String identifier, String password, String ipAddress, String userAgent) {
        loginAttemptService.assertAllowed(identifier, ipAddress);

        Optional<UserAccount> candidate = userAccountRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier.trim(), identifier.trim());

        // Always run one bcrypt comparison. Skipping it for unknown users made "no such user" answer
        // ~100 ms faster than "wrong password", which reveals which usernames and emails exist.
        boolean passwordMatches = candidate.isPresent()
                ? passwordEncoder.matches(password, candidate.get().getPasswordHash())
                : burnPasswordCheck(password);
        if (!passwordMatches) {
            loginAttemptService.recordFailure(identifier, ipAddress);
            throw new InvalidCredentialsException();
        }

        UserAccount user = candidate.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            loginAttemptService.recordFailure(identifier, ipAddress);
            throw new AccountNotActiveException();
        }
        if (!user.isEmailVerified()) {
            // Only reached with a correct password, so this does not reveal which emails exist.
            throw new EmailNotVerifiedException();
        }

        loginAttemptService.clear(identifier, ipAddress);
        user.setLastLoginAt(Instant.now());
        userAccountRepository.save(user);

        TokenIssuanceService.IssuedAccessToken accessToken = tokenIssuanceService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.create(user, userAgent, ipAddress);

        return AuthMapper.toTokenResponse(user, accessToken, refreshToken.rawToken());
    }

    /**
     * Spends the same work as a real password check, against a hash no password can match, so the
     * response time does not depend on whether the account exists. Always returns false.
     */
    private boolean burnPasswordCheck(String password) {
        String dummy = timingEqualizerHash;
        if (dummy == null) {
            // Encoded lazily with the configured encoder so the cost factor matches real hashes.
            dummy = passwordEncoder.encode(UUID.randomUUID().toString());
            timingEqualizerHash = dummy;
        }
        if (dummy != null) {
            passwordEncoder.matches(password, dummy);
        }
        return false;
    }

    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, AccountNotActiveException.class})
    public TokenResponse refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        RefreshTokenService.RotatedRefreshToken rotated =
                refreshTokenService.rotate(rawRefreshToken, userAgent, ipAddress);

        TokenIssuanceService.IssuedAccessToken accessToken =
                tokenIssuanceService.issueAccessToken(rotated.user());

        return AuthMapper.toTokenResponse(rotated.user(), accessToken, rotated.rawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken, String rawAccessToken) {
        refreshTokenService.revoke(rawRefreshToken);
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            return;
        }
        try {
            Claims claims = tokenIssuanceService.parse(rawAccessToken);
            tokenIssuanceService.blacklistAccessToken(
                    rawAccessToken,
                    claims.getSubject(),
                    claims.getId(),
                    claims.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException exception) {
            // An expired token is already unusable; nothing to blacklist.
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException("Access token is invalid");
        }
    }
}
