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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private OtpService otpService;

    @InjectMocks
    private EmailVerificationService service;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = new UserAccount();
        user.setId(7L);
        user.setPublicId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);
        user.setCreatedAt(Instant.now());
    }

    private void userExists() {
        when(userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("alice@example.com", "alice@example.com"))
                .thenReturn(Optional.of(user));
    }

    @Test
    void verifyMarksTheEmailVerifiedWithoutSigningTheUserIn() {
        userExists();

        service.verify(" alice@example.com ", "123456", "1.2.3.4");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getLastLoginAt()).as("verifying is not a sign-in").isNull();
        verify(otpService).verify(OtpPurpose.EMAIL_VERIFICATION, user.getPublicId(), "123456");
        verify(userAccountRepository).save(user);
    }

    @Test
    void wrongCodeLeavesTheAccountUnverified() {
        userExists();
        doThrow(OtpService.invalidCode()).when(otpService)
                .verify(OtpPurpose.EMAIL_VERIFICATION, user.getPublicId(), "000000");

        assertThatThrownBy(() -> service.verify("alice@example.com", "000000", "1.2.3.4"))
                .isInstanceOf(AuthException.class);

        assertThat(user.isEmailVerified()).isFalse();
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void unknownIdentifierGetsTheSameGenericError() {
        when(userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("ghost", "ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("ghost", "123456", "1.2.3.4"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid or expired code");
        verify(otpService, never()).verify(any(), any(), any());
    }

    @Test
    void alreadyVerifiedAccountIsToldToSignIn() {
        user.setEmailVerified(true);
        userExists();

        assertThatThrownBy(() -> service.verify("alice@example.com", "123456", "1.2.3.4"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void resendIssuesACodeOnlyForUnverifiedActiveAccounts() {
        userExists();

        service.resend("alice@example.com", "1.2.3.4");

        verify(otpService).issue(OtpPurpose.EMAIL_VERIFICATION, user.getPublicId());
    }

    @Test
    void resendDoesNothingForVerifiedOrUnknownAccounts() {
        user.setEmailVerified(true);
        userExists();
        when(userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("ghost", "ghost"))
                .thenReturn(Optional.empty());

        service.resend("alice@example.com", "1.2.3.4");
        service.resend("ghost", "1.2.3.4");

        verify(otpService, never()).issue(any(), any());
    }

    @Test
    void resendSwallowsThrottlingSoTheResponseStaysGeneric() {
        userExists();
        doThrow(new AuthException(HttpStatus.TOO_MANY_REQUESTS, "slow down")).when(otpService)
                .issue(OtpPurpose.EMAIL_VERIFICATION, user.getPublicId());

        service.resend("alice@example.com", "1.2.3.4"); // must not throw
    }
}
