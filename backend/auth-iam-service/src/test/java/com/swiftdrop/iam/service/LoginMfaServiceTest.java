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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link LoginMfaService} against an in-memory Redis stand-in and a mocked {@link OtpService}. */
class LoginMfaServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private StringRedisTemplate redis;
    private OtpService otpService;
    private UserAccountRepository users;
    private UserAccount admin;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        doAnswer(i -> store.put(i.getArgument(0), i.getArgument(1)))
                .when(ops).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);

        otpService = mock(OtpService.class);
        users = mock(UserAccountRepository.class);

        admin = new UserAccount();
        admin.setPublicId(UUID.randomUUID());
        admin.setRole(UserRole.SYSTEM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        when(users.findByPublicId(admin.getPublicId())).thenReturn(Optional.of(admin));
    }

    private LoginMfaService service(boolean enabled) {
        return new LoginMfaService(enabled, redis, otpService, new OtpProperties(),
                mock(TokenIssuanceService.class, i -> "h" + Integer.toHexString(i.getArgument(0).hashCode())), users);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void onlyPrivilegedRolesNeedASecondFactorAndOnlyWhenEnabled(UserRole role) {
        admin.setRole(role);
        boolean privileged = role == UserRole.SYSTEM_ADMIN || role == UserRole.DISPATCHER;
        assertThat(service(true).isRequiredFor(admin)).isEqualTo(privileged);
        assertThat(service(false).isRequiredFor(admin)).isFalse();
    }

    @Test
    void startEmailsACodeAndStoresOnlyAHashOfTheChallenge() {
        MfaChallengeResponse challenge = service(true).start(admin);

        verify(otpService).issue(OtpPurpose.LOGIN_MFA, admin.getPublicId());
        assertThat(challenge.mfaRequired()).isTrue();
        assertThat(challenge.challengeId()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(store).hasSize(1);
        assertThat(store.keySet().iterator().next()).doesNotContain(challenge.challengeId());
    }

    @Test
    void completeWithTheRightCodeReturnsTheUserAndClosesTheChallenge() {
        LoginMfaService mfa = service(true);
        MfaChallengeResponse challenge = mfa.start(admin);

        assertThat(mfa.complete(challenge.challengeId(), "123456")).isSameAs(admin);
        verify(otpService).verify(OtpPurpose.LOGIN_MFA, admin.getPublicId(), "123456");
        assertThat(store).isEmpty();
        // The challenge is single use.
        assertThatThrownBy(() -> mfa.complete(challenge.challengeId(), "123456"))
                .isInstanceOf(AuthException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownChallengeIsRejected() {
        assertThatThrownBy(() -> service(true).complete("made-up", "123456"))
                .isInstanceOf(AuthException.class)
                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongCodeKeepsTheChallengeButTooManyAttemptsClosesIt() {
        LoginMfaService mfa = service(true);
        String challengeId = mfa.start(admin).challengeId();

        doThrow(OtpService.invalidCode()).when(otpService).verify(OtpPurpose.LOGIN_MFA, admin.getPublicId(), "000000");
        assertThatThrownBy(() -> mfa.complete(challengeId, "000000")).isInstanceOf(AuthException.class);
        assertThat(store).hasSize(1);

        doThrow(new AuthException(HttpStatus.TOO_MANY_REQUESTS, "Too many"))
                .when(otpService).verify(OtpPurpose.LOGIN_MFA, admin.getPublicId(), "111111");
        assertThatThrownBy(() -> mfa.complete(challengeId, "111111")).isInstanceOf(AuthException.class);
        assertThat(store).isEmpty();
    }

    @Test
    void accountDisabledDuringTheChallengeCannotFinishSigningIn() {
        LoginMfaService mfa = service(true);
        String challengeId = mfa.start(admin).challengeId();
        admin.setStatus(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> mfa.complete(challengeId, "123456")).isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void redisOutageAnswers503() {
        when(redis.opsForValue().get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertThatThrownBy(() -> service(true).complete("any", "123456"))
                .isInstanceOf(AuthException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
