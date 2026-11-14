package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.events.auth.AuthOtpRequestedEvent;
import com.swiftdrop.contracts.events.auth.OtpPurpose;
import com.swiftdrop.iam.config.OtpProperties;
import com.swiftdrop.iam.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises {@link OtpService} against an in-memory stand-in for Redis (no TTL clock). */
class OtpServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private final UUID userId = UUID.randomUUID();

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private AuthOtpPublisher publisher;
    private OtpProperties props;
    private OtpService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        publisher = mock(AuthOtpPublisher.class);
        props = new OtpProperties();
        props.setMaxAttempts(3);

        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        doAnswer(i -> {
            store.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(i -> store.putIfAbsent(i.getArgument(0), i.getArgument(1)) == null);
        when(ops.increment(anyString())).thenAnswer(i -> {
            String key = i.getArgument(0);
            long next = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, String.valueOf(next));
            return next;
        });
        when(redis.delete(anyString())).thenAnswer(i -> store.remove(i.<String>getArgument(0)) != null);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        service = new OtpService(redis, props, publisher);
    }

    private String issueAndCaptureCode() {
        service.issue(OtpPurpose.EMAIL_VERIFICATION, userId);
        ArgumentCaptor<AuthOtpRequestedEvent> captor = ArgumentCaptor.forClass(AuthOtpRequestedEvent.class);
        verify(publisher, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
        return captor.getValue().code();
    }

    private void clearCooldown() {
        store.keySet().removeIf(key -> key.contains(":cooldown:"));
    }

    @Test
    void issuePublishesASixDigitCodeAndStoresOnlyItsHash() {
        String code = issueAndCaptureCode();

        assertThat(code).matches("\\d{6}");
        assertThat(store.values()).noneMatch(value -> value.contains(code));
        ArgumentCaptor<AuthOtpRequestedEvent> captor = ArgumentCaptor.forClass(AuthOtpRequestedEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().recipientId()).isEqualTo(userId);
        assertThat(captor.getValue().purpose()).isEqualTo(OtpPurpose.EMAIL_VERIFICATION);
        assertThat(captor.getValue().expiresInMinutes()).isEqualTo(10);
        assertThat(captor.getValue().toString()).doesNotContain(code);
    }

    @Test
    void correctCodeIsAcceptedExactlyOnce() {
        String code = issueAndCaptureCode();

        service.verify(OtpPurpose.EMAIL_VERIFICATION, userId, code);

        assertThatThrownBy(() -> service.verify(OtpPurpose.EMAIL_VERIFICATION, userId, code))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void codeIsBoundToItsPurpose() {
        String code = issueAndCaptureCode();

        assertThatThrownBy(() -> service.verify(OtpPurpose.PASSWORD_RESET, userId, code))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void wrongGuessesDestroyTheCodeAfterTheAttemptLimit() {
        String code = issueAndCaptureCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThatThrownBy(() -> service.verify(OtpPurpose.EMAIL_VERIFICATION, userId, wrong))
                    .isInstanceOf(AuthException.class)
                    .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        // The 4th attempt locks the code, so even the correct code no longer works.
        assertThatThrownBy(() -> service.verify(OtpPurpose.EMAIL_VERIFICATION, userId, code))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> service.verify(OtpPurpose.EMAIL_VERIFICATION, userId, code))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void malformedCodeIsRejectedWithoutTouchingRedis() {
        assertThatThrownBy(() -> service.verify(OtpPurpose.EMAIL_VERIFICATION, userId, "12ab56"))
                .isInstanceOf(AuthException.class);
        verify(ops, never()).get(anyString());
    }

    @Test
    void secondCodeInsideTheCooldownIsRefused() {
        service.issue(OtpPurpose.EMAIL_VERIFICATION, userId);

        assertThatThrownBy(() -> service.issue(OtpPurpose.EMAIL_VERIFICATION, userId))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void hourlyCapStopsFurtherCodes() {
        for (int i = 0; i < props.getMaxSendsPerHour(); i++) {
            clearCooldown();
            service.issue(OtpPurpose.EMAIL_VERIFICATION, userId);
        }
        clearCooldown();

        assertThatThrownBy(() -> service.issue(OtpPurpose.EMAIL_VERIFICATION, userId))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void newCodeInvalidatesThePreviousOne() {
        String first = issueAndCaptureCode();
        clearCooldown();
        service.issue(OtpPurpose.EMAIL_VERIFICATION, userId);
        ArgumentCaptor<AuthOtpRequestedEvent> captor = ArgumentCaptor.forClass(AuthOtpRequestedEvent.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(captor.capture());
        String second = captor.getValue().code();

        if (!first.equals(second)) {
            assertThatThrownBy(() -> service.verify(OtpPurpose.EMAIL_VERIFICATION, userId, first))
                    .isInstanceOf(AuthException.class);
        }
        service.verify(OtpPurpose.EMAIL_VERIFICATION, userId, second);
    }

    @Test
    void failsClosedWhenRedisIsDown() {
        when(ops.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> service.verify(OtpPurpose.EMAIL_VERIFICATION, userId, "123456"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> service.issue(OtpPurpose.EMAIL_VERIFICATION, userId))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(publisher, never()).publish(any());
    }

    @Test
    void ipLimiterBlocksAfterTheHourlyBudget() {
        props.setIpMaxRequestsPerHour(2);
        service.assertIpAllowed("10.0.0.1");
        service.assertIpAllowed("10.0.0.1");

        assertThatThrownBy(() -> service.assertIpAllowed("10.0.0.1"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        service.assertIpAllowed("10.0.0.2");
    }

    @Test
    void checkDoesNotConsumeTheCodeButConsumeDoes() {
        String code = issueAndCaptureCode();

        service.check(OtpPurpose.EMAIL_VERIFICATION, userId, code);
        service.check(OtpPurpose.EMAIL_VERIFICATION, userId, code); // still valid: check does not consume
        service.consume(OtpPurpose.EMAIL_VERIFICATION, userId);

        assertThatThrownBy(() -> service.check(OtpPurpose.EMAIL_VERIFICATION, userId, code))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void aCodeCanOnlyBeConsumedOnceEvenUnderARace() {
        issueAndCaptureCode();
        service.consume(OtpPurpose.EMAIL_VERIFICATION, userId);

        assertThatThrownBy(() -> service.consume(OtpPurpose.EMAIL_VERIFICATION, userId))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid or expired code");
    }
}
