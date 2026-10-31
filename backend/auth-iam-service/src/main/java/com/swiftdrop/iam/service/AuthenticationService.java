package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.exception.AccountNotActiveException;
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

@Service
public class AuthenticationService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuanceService tokenIssuanceService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

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

        if (candidate.isEmpty() || !passwordEncoder.matches(password, candidate.get().getPasswordHash())) {
            loginAttemptService.recordFailure(identifier, ipAddress);
            throw new InvalidCredentialsException();
        }

        UserAccount user = candidate.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            loginAttemptService.recordFailure(identifier, ipAddress);
            throw new AccountNotActiveException();
        }

        loginAttemptService.clear(identifier, ipAddress);
        user.setLastLoginAt(Instant.now());
        userAccountRepository.save(user);

        TokenIssuanceService.IssuedAccessToken accessToken = tokenIssuanceService.issueAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.create(user, userAgent, ipAddress);

        return AuthMapper.toTokenResponse(user, accessToken, refreshToken.rawToken());
    }

    @Transactional
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
