package com.swiftdrop.webhook.delivery;

import com.swiftdrop.webhook.domain.entity.AttemptErrorClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Retry schedule, permanent failures and Retry-After handling of outbound webhooks. */
class DeliveryRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final List<Duration> DELAYS =
            List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10));

    private final DeliveryRetryPolicy policy =
            new DeliveryRetryPolicy(properties(0.0), Clock.fixed(NOW, ZoneOffset.UTC), null);

    private static DeliveryProperties properties(double jitter) {
        return new DeliveryProperties(DELAYS, jitter, Duration.ofMinutes(10), 4, 1, 10,
                Duration.ofSeconds(2), Duration.ofSeconds(60));
    }

    private static AttemptOutcome failure(Integer status, AttemptErrorClass error, String retryAfter) {
        return new AttemptOutcome(status, error, true, 10, retryAfter);
    }

    @Test
    void successIsFinal() {
        RetryDecision decision = policy.decide(1, new AttemptOutcome(200, null, false, 5, null));
        assertThat(decision.kind()).isEqualTo(RetryDecision.Kind.SUCCEEDED);
        assertThat(decision.nextAttemptAt()).isNull();
    }

    @Test
    void transientFailuresFollowTheBackoffSchedule() {
        assertThat(policy.decide(1, failure(500, AttemptErrorClass.HTTP_SERVER_ERROR, null)).delay())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.decide(2, failure(502, AttemptErrorClass.HTTP_SERVER_ERROR, null)).delay())
                .isEqualTo(Duration.ofMinutes(2));
        RetryDecision third = policy.decide(3, failure(null, AttemptErrorClass.TIMEOUT, null));
        assertThat(third.delay()).isEqualTo(Duration.ofMinutes(10));
        assertThat(third.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void lastAttemptFailureGoesToTheDeadLetterState() {
        RetryDecision decision = policy.decide(4, failure(503, AttemptErrorClass.HTTP_SERVER_ERROR, null));
        assertThat(decision.kind()).isEqualTo(RetryDecision.Kind.DEAD);
        assertThat(decision.deadReason()).isEqualTo(RetryDecision.DeadReason.ATTEMPTS_EXHAUSTED);
    }

    @ParameterizedTest
    @CsvSource({"404,HTTP_CLIENT_ERROR", "301,INVALID_RESPONSE", ",BLOCKED_DESTINATION"})
    void permanentFailuresAreNotRetried(Integer status, AttemptErrorClass error) {
        RetryDecision decision = policy.decide(1, failure(status, error, null));
        assertThat(decision.kind()).isEqualTo(RetryDecision.Kind.DEAD);
        assertThat(decision.deadReason()).isEqualTo(RetryDecision.DeadReason.PERMANENT_FAILURE);
    }

    @Test
    void requestTimeoutAndTooEarlyAreRetried() {
        assertThat(policy.decide(1, failure(408, AttemptErrorClass.HTTP_CLIENT_ERROR, null)).kind())
                .isEqualTo(RetryDecision.Kind.RETRY);
        assertThat(policy.decide(1, failure(425, AttemptErrorClass.HTTP_CLIENT_ERROR, null)).kind())
                .isEqualTo(RetryDecision.Kind.RETRY);
    }

    @Test
    void retryAfterLongerThanBackoffIsUsedButCapped() {
        RetryDecision honoured = policy.decide(1, failure(429, AttemptErrorClass.HTTP_RATE_LIMITED, "120"));
        assertThat(honoured.delay()).isEqualTo(Duration.ofSeconds(120));
        assertThat(honoured.retryAfterUsed()).isTrue();

        RetryDecision capped = policy.decide(1, failure(503, AttemptErrorClass.HTTP_SERVER_ERROR, "86400"));
        assertThat(capped.delay()).isEqualTo(Duration.ofMinutes(10));

        RetryDecision shorter = policy.decide(2, failure(429, AttemptErrorClass.HTTP_RATE_LIMITED, "5"));
        assertThat(shorter.delay()).isEqualTo(Duration.ofMinutes(2));
        assertThat(shorter.retryAfterUsed()).isFalse();
    }

    @Test
    void retryAfterParsesSecondsAndHttpDatesAndIgnoresGarbage() {
        assertThat(DeliveryRetryPolicy.parseRetryAfter("30", NOW)).contains(Duration.ofSeconds(30));
        assertThat(DeliveryRetryPolicy.parseRetryAfter("Tue, 17 Nov 2026 10:01:00 GMT", NOW))
                .contains(Duration.ofMinutes(1));
        assertThat(DeliveryRetryPolicy.parseRetryAfter("Tuesday, 17-Nov-26 10:02:00 GMT", NOW))
                .contains(Duration.ofMinutes(2));
        assertThat(DeliveryRetryPolicy.parseRetryAfter("Tue Nov 17 10:03:00 2026", NOW))
                .contains(Duration.ofMinutes(3));
        assertThat(DeliveryRetryPolicy.parseRetryAfter("0", NOW)).isEmpty();
        assertThat(DeliveryRetryPolicy.parseRetryAfter("-5", NOW)).isEmpty();
        assertThat(DeliveryRetryPolicy.parseRetryAfter("soon", NOW)).isEmpty();
        assertThat(DeliveryRetryPolicy.parseRetryAfter("Mon, 16 Nov 2026 10:00:00 GMT", NOW)).isEmpty();
        assertThat(DeliveryRetryPolicy.parseRetryAfter(null, NOW)).isEmpty();
    }

    @Test
    void jitterStaysWithinTheConfiguredRatio() {
        DeliveryRetryPolicy jittered = new DeliveryRetryPolicy(properties(0.2));
        for (int i = 0; i < 50; i++) {
            Duration delay = jittered.decide(1, failure(500, AttemptErrorClass.HTTP_SERVER_ERROR, null)).delay();
            assertThat(delay).isBetween(Duration.ofSeconds(24), Duration.ofSeconds(36));
        }
        assertThat(jittered.maxAttempts()).isEqualTo(4);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> policy.decide(0, failure(500, AttemptErrorClass.HTTP_SERVER_ERROR, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.decide(1, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
