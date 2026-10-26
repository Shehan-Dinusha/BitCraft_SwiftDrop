package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.TokenResponse;
import com.swiftdrop.iam.exception.AccountNotActiveException;
import com.swiftdrop.iam.exception.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenIssuanceService tokenIssuanceService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthenticationService authenticationService;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = new UserAccount();
        user.setId(1L);
        user.setPublicId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.CUSTOMER);
        user.setDistrict(SriLankanDistrict.COLOMBO);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Instant.now());
    }

    @Test
    void loginIssuesTokensAndClearsFailures() {
        when(userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("alice", "alice"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "hash")).thenReturn(true);
        when(tokenIssuanceService.issueAccessToken(user))
                .thenReturn(new TokenIssuanceService.IssuedAccessToken(
                        "access", "jti", Instant.now().plusSeconds(3600), 3600));
        when(refreshTokenService.create(user, "agent", "127.0.0.1"))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken(
                        "refresh", Instant.now().plusSeconds(3600)));

        TokenResponse response = authenticationService.login("alice", "Secret123", "127.0.0.1", "agent");

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(response.userId()).isEqualTo(user.getPublicId().toString());
        verify(loginAttemptService).clear("alice", "127.0.0.1");
        verify(userAccountRepository).save(user);
    }

    @Test
    void loginWithWrongPasswordRecordsFailure() {
        when(userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("alice", "alice"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login("alice", "wrong", "127.0.0.1", "agent"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure("alice", "127.0.0.1");
        verify(refreshTokenService, never()).create(any(), any(), any());
    }

    @Test
    void loginRejectsInactiveAccount() {
        user.setStatus(UserStatus.SUSPENDED);
        when(userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("alice", "alice"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.login("alice", "Secret123", "127.0.0.1", "agent"))
                .isInstanceOf(AccountNotActiveException.class);

        verify(loginAttemptService).recordFailure("alice", "127.0.0.1");
    }

    @Test
    void refreshRotatesAndIssuesNewAccessToken() {
        when(refreshTokenService.rotate("old-refresh", "agent", "127.0.0.1"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(user, "new-refresh"));
        when(tokenIssuanceService.issueAccessToken(user))
                .thenReturn(new TokenIssuanceService.IssuedAccessToken(
                        "new-access", "jti", Instant.now().plusSeconds(3600), 3600));

        TokenResponse response = authenticationService.refresh("old-refresh", "127.0.0.1", "agent");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void logoutRevokesRefreshAndBlacklistsAccessToken() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(user.getPublicId().toString());
        when(claims.getId()).thenReturn("jti");
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(tokenIssuanceService.parse("access")).thenReturn(claims);

        authenticationService.logout("refresh", "access");

        verify(refreshTokenService).revoke("refresh");
        verify(tokenIssuanceService).blacklistAccessToken(
                eq("access"), eq(user.getPublicId().toString()), eq("jti"), any(Instant.class));
    }
}
