package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.config.JwtConfig;
import com.swiftdrop.iam.domain.entity.RefreshToken;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.RefreshTokenRepository;
import com.swiftdrop.iam.exception.AccountNotActiveException;
import com.swiftdrop.iam.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenIssuanceService tokenIssuanceService;

    private RefreshTokenService refreshTokenService;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setRefreshTokenExpirationDays(30);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, tokenIssuanceService, config);

        user = new UserAccount();
        user.setId(1L);
        user.setPublicId(UUID.randomUUID());
        user.setUsername("alice");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void createPersistsOnlyTheHash() {
        when(tokenIssuanceService.generateRefreshToken()).thenReturn("raw-token");
        when(tokenIssuanceService.hashToken("raw-token")).thenReturn("hash");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken issued =
                refreshTokenService.create(user, "agent", "127.0.0.1");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(issued.rawToken()).isEqualTo("raw-token");
        assertThat(issued.expiresAt()).isAfter(Instant.now());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hash");
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void reuseOfRevokedTokenRevokesWholeFamily() {
        RefreshToken revoked = token("hash", user, Instant.now().plusSeconds(3600));
        revoked.setRevoked(true);
        revoked.setReplacedByTokenHash("replacement-hash");
        when(tokenIssuanceService.hashToken("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw", "agent", "127.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).revokeAllForUser(user);
        verify(tokenIssuanceService).blacklistUser(user.getPublicId());
    }

    @Test
    void expiredTokenIsRejectedAndMarkedRevoked() {
        RefreshToken expired = token("hash", user, Instant.now().minusSeconds(1));
        when(tokenIssuanceService.hashToken("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(expired));
        when(refreshTokenRepository.save(expired)).thenReturn(expired);

        assertThatThrownBy(() -> refreshTokenService.rotate("raw", "agent", "127.0.0.1"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(expired.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(expired);
    }

    @Test
    void inactiveUserCannotRotateToken() {
        user.setStatus(UserStatus.SUSPENDED);
        RefreshToken token = token("hash", user, Instant.now().plusSeconds(3600));
        when(tokenIssuanceService.hashToken("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw", "agent", "127.0.0.1"))
                .isInstanceOf(AccountNotActiveException.class);

        verify(refreshTokenRepository).revokeAllForUser(user);
        verify(refreshTokenRepository, never()).save(token);
    }

    @Test
    void rotationMarksOldTokenAndReturnsNewRawToken() {
        RefreshToken current = token("hash", user, Instant.now().plusSeconds(3600));
        when(tokenIssuanceService.hashToken("raw")).thenReturn("hash");
        when(tokenIssuanceService.generateRefreshToken()).thenReturn("new-raw");
        when(tokenIssuanceService.hashToken("new-raw")).thenReturn("new-hash");
        when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(current));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RotatedRefreshToken rotated =
                refreshTokenService.rotate("raw", "agent", "127.0.0.1");

        assertThat(rotated.rawToken()).isEqualTo("new-raw");
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedByTokenHash()).isEqualTo("new-hash");
    }

    private RefreshToken token(String hash, UserAccount owner, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash);
        token.setUser(owner);
        token.setExpiresAt(expiresAt);
        token.setRevoked(false);
        return token;
    }
}
