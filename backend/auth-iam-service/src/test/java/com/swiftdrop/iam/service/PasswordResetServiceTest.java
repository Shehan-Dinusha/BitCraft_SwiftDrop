package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.UserStatus;
import com.swiftdrop.contracts.events.auth.OtpPurpose;
import com.swiftdrop.iam.domain.entity.UserAccount;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private OtpService otpService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicy passwordPolicy;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TokenIssuanceService tokenIssuanceService;

    @InjectMocks
    private PasswordResetService service;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = new UserAccount();
        user.setId(9L);
        user.setPublicId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("old-hash");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Instant.now());
    }

    private void userExists() {
        when(userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("alice@example.com", "alice@example.com"))
                .thenReturn(Optional.of(user));
    }

    @Test
    void requestIssuesAResetCodeForAnActiveAccount() {
        userExists();

        service.request("alice@example.com", "1.2.3.4");

        verify(otpService).issue(OtpPurpose.PASSWORD_RESET, user.getPublicId());
    }

    @Test
    void requestDoesNothingForUnknownOrSuspendedAccounts() {
        when(userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("ghost", "ghost"))
                .thenReturn(Optional.empty());
        service.request("ghost", "1.2.3.4");

        user.setStatus(UserStatus.SUSPENDED);
        userExists();
        service.request("alice@example.com", "1.2.3.4");

        verify(otpService, never()).issue(any(), any());
    }

    @Test
    void resetSetsThePasswordConsumesTheCodeAndRevokesEverySession() {
        userExists();
        when(passwordEncoder.matches("NewSecret123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewSecret123")).thenReturn("new-hash");

        service.reset("alice@example.com", "123456", "NewSecret123", "1.2.3.4");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isEmailVerified()).isTrue();
        InOrder order = inOrder(otpService);
        order.verify(otpService).check(OtpPurpose.PASSWORD_RESET, user.getPublicId(), "123456");
        order.verify(otpService).consume(OtpPurpose.PASSWORD_RESET, user.getPublicId());
        verify(refreshTokenService).revokeAll(user);
        verify(tokenIssuanceService).blacklistUser(user.getPublicId());
        verify(userAccountRepository).save(user);
    }

    @Test
    void reusingTheCurrentPasswordIsRejectedAndTheCodeStaysUsable() {
        userExists();
        when(passwordEncoder.matches("OldSecret123", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.reset("alice@example.com", "123456", "OldSecret123", "1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different from your current password");

        verify(otpService).check(OtpPurpose.PASSWORD_RESET, user.getPublicId(), "123456");
        verify(otpService, never()).consume(any(), any());
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void weakPasswordIsRejectedBeforeTheCodeIsEvenChecked() {
        userExists();
        doThrow(new IllegalArgumentException("Password must be at least 8 characters"))
                .when(passwordPolicy).validate("short");

        assertThatThrownBy(() -> service.reset("alice@example.com", "123456", "short", "1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(otpService, never()).check(any(), any(), any());
        verify(otpService, never()).consume(any(), any());
    }

    @Test
    void accountSpecificRulesRunOnlyAfterAValidCode() {
        userExists();
        doThrow(OtpService.invalidCode()).when(otpService)
                .check(OtpPurpose.PASSWORD_RESET, user.getPublicId(), "000000");

        assertThatThrownBy(() -> service.reset("alice@example.com", "000000", "alice12345", "1.2.3.4"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid or expired code");

        // Without a valid code the caller must not learn anything about the username or email.
        verify(passwordPolicy, never()).validate(anyString(), anyString(), anyString());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void wrongCodeKeepsTheOldPassword() {
        userExists();
        doThrow(OtpService.invalidCode()).when(otpService)
                .check(OtpPurpose.PASSWORD_RESET, user.getPublicId(), "000000");

        assertThatThrownBy(() -> service.reset("alice@example.com", "000000", "NewSecret123", "1.2.3.4"))
                .isInstanceOf(AuthException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void unknownAccountGetsTheGenericInvalidCodeError() {
        when(userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("ghost", "ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset("ghost", "123456", "NewSecret123", "1.2.3.4"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid or expired code");
    }
}
