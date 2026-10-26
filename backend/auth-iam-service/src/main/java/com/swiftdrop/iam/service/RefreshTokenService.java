package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.domain.entity.RefreshToken;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.RefreshTokenRepository;
import com.swiftdrop.iam.exception.AccountNotActiveException;
import com.swiftdrop.iam.exception.InvalidRefreshTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenIssuanceService tokenIssuanceService;
    private final JwtConfig jwtConfig;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               TokenIssuanceService tokenIssuanceService,
                               JwtConfig jwtConfig) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenIssuanceService = tokenIssuanceService;
        this.jwtConfig = jwtConfig;
    }

    @Transactional
    public IssuedRefreshToken create(UserAccount user, String userAgent, String ipAddress) {
        return createInternal(user, userAgent, ipAddress);
    }

    @Transactional
    public RotatedRefreshToken rotate(String rawToken, String userAgent, String ipAddress) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is required");
        }

        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(
                        tokenIssuanceService.hashToken(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));

        if (current.isRevoked()) {
            if (current.getReplacedByTokenHash() != null) {
                refreshTokenRepository.revokeAllForUser(current.getUser());
                tokenIssuanceService.blacklistUser(current.getUser().getPublicId());
            }
            throw new InvalidRefreshTokenException("Refresh token is no longer valid");
        }

        if (!current.isActive(Instant.now())) {
            current.setRevoked(true);
            refreshTokenRepository.save(current);
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        UserAccount user = current.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            revokeAll(user);
            throw new AccountNotActiveException();
        }

        IssuedRefreshToken replacement = createInternal(user, userAgent, ipAddress);
        current.setRevoked(true);
        current.setReplacedByTokenHash(tokenIssuanceService.hashToken(replacement.rawToken()));
        refreshTokenRepository.save(current);

        return new RotatedRefreshToken(user, replacement.rawToken());
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(tokenIssuanceService.hashToken(rawToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAll(UserAccount user) {
        refreshTokenRepository.revokeAllForUser(user);
    }

    @Transactional
    public int purgeExpiredAndRevoked() {
        return refreshTokenRepository.deleteExpiredOrRevoked(Instant.now().minus(Duration.ofDays(1)));
    }

    private IssuedRefreshToken createInternal(UserAccount user, String userAgent, String ipAddress) {
        String rawToken = tokenIssuanceService.generateRefreshToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(jwtConfig.getRefreshTokenExpirationDays()));

        RefreshToken token = new RefreshToken();
        token.setTokenHash(tokenIssuanceService.hashToken(rawToken));
        token.setExpiresAt(expiresAt);
        token.setUser(user);
        token.setRevoked(false);
        token.setUserAgent(truncate(userAgent, 512));
        token.setIpAddress(truncate(ipAddress, 64));
        refreshTokenRepository.save(token);

        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record IssuedRefreshToken(String rawToken, Instant expiresAt) {
    }

    public record RotatedRefreshToken(UserAccount user, String rawToken) {
    }
}
