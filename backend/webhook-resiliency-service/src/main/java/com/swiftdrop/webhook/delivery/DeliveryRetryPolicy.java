package com.swiftdrop.webhook.delivery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Decides what happens to a delivery job after one attempt (decisions D1–D3). Pure: no I/O. The clock and random
 * source are injectable, so a decision can be reproduced exactly. Persisting the decision (job status,
 * {@code next_attempt_at}, attempt row) is Step 9; running attempts is Step 10.
 *
 * <p><b>Attempt semantics.</b> {@code attemptNumber} is the 1-based number of the attempt that has just been made
 * (it equals {@code webhook_deliveries.attempt_count} after the attempt). With the default schedule there are at most
 * <b>4 attempts in total</b>: attempt 1 immediately, attempt 2 ≈30 s after attempt 1 fails, attempt 3 ≈2 min after
 * attempt 2 fails, attempt 4 ≈10 min after attempt 3 fails. A failure of attempt 4 (or any attempt number at or above
 * the maximum) is DEAD; a RETRY beyond the limit can never be produced.</p>
 *
 * <p><b>Order of rules.</b> (1) 2xx → SUCCEEDED. (2) Not retryable per D3 (3xx, other 4xx, blocked destination) →
 * DEAD (PERMANENT_FAILURE) at once. Retryability is {@link WebhookHttpSender#retryable}, the sender's own rule, so the
 * two cannot drift; {@code UNKNOWN} is retryable. (3) Last allowed attempt → DEAD (ATTEMPTS_EXHAUSTED).
 * (4) Otherwise RETRY after max(jittered backoff, capped Retry-After).</p>
 *
 * <p><b>Jitter (D1).</b> Backoff = configured delay × uniform factor in [1 − r, 1 + r), r = 0.2 by default.</p>
 *
 * <p><b>Retry-After (D2).</b> Only for HTTP 429 and 503. Accepts delta-seconds and the three HTTP-date forms
 * (IMF-fixdate, RFC 850, asctime; GMT). The requested delay is capped at {@code retry-after-cap} (10 min) and is only
 * used if longer than the jittered backoff, so it never shortens the schedule. Missing, malformed, negative, zero or
 * past values are ignored (normal backoff). Retry-After never changes the attempt limit.</p>
 */
@Component
@EnableConfigurationProperties(DeliveryProperties.class)
public class DeliveryRetryPolicy {

    private static final Pattern DELTA_SECONDS = Pattern.compile("^[0-9]+$");
    /** Longer digit strings are clamped instead of parsed, so overflow is impossible; the cap applies anyway. */
    private static final int MAX_DELTA_DIGITS = 10;

    private static final DateTimeFormatter RFC_850 = new DateTimeFormatterBuilder()
            .appendPattern("EEEE, dd-MMM-")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1970)
            .appendPattern(" HH:mm:ss 'GMT'")
            .toFormatter(Locale.US);
    private static final DateTimeFormatter ASCTIME = DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.US);

    private final DeliveryProperties properties;
    private final Clock clock;
    private final RandomGenerator random; // null: ThreadLocalRandom per call (thread-safe)

    @Autowired
    public DeliveryRetryPolicy(DeliveryProperties properties) {
        this(properties, Clock.systemUTC(), null);
    }

    public DeliveryRetryPolicy(DeliveryProperties properties, Clock clock, RandomGenerator random) {
        this.properties = properties;
        this.clock = clock;
        this.random = random;
    }

    public int maxAttempts() {
        return properties.maxAttempts();
    }

    public RetryDecision decide(int attemptNumber, AttemptOutcome outcome) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be at least 1");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (outcome.success()) {
            return new RetryDecision(RetryDecision.Kind.SUCCEEDED, attemptNumber, null, null, false, null, null);
        }
        if (!WebhookHttpSender.retryable(outcome.httpStatus(), outcome.errorClass())) {
            return dead(attemptNumber, outcome, RetryDecision.DeadReason.PERMANENT_FAILURE);
        }
        if (attemptNumber >= properties.maxAttempts()) {
            return dead(attemptNumber, outcome, RetryDecision.DeadReason.ATTEMPTS_EXHAUSTED);
        }

        Instant now = clock.instant();
        Duration backoff = jittered(properties.retryDelays().get(attemptNumber - 1));
        Duration delay = backoff;
        boolean retryAfterUsed = false;
        Integer status = outcome.httpStatus();
        if (status != null && (status == 429 || status == 503)) {
            Optional<Duration> requested = parseRetryAfter(outcome.retryAfter(), now);
            if (requested.isPresent()) {
                Duration capped = min(requested.get(), properties.retryAfterCap());
                if (capped.compareTo(backoff) > 0) {
                    delay = capped;
                    retryAfterUsed = true;
                }
            }
        }
        return new RetryDecision(RetryDecision.Kind.RETRY, attemptNumber, now.plus(delay), delay, retryAfterUsed,
                outcome.errorClass(), null);
    }

    /**
     * Delay requested by a {@code Retry-After} value, uncapped; empty when absent, malformed, zero or in the past.
     * Delta-seconds: unsigned digits only. HTTP-date: IMF-fixdate ({@code Sun, 06 Nov 1994 08:49:37 GMT}),
     * RFC 850 ({@code Sunday, 06-Nov-94 08:49:37 GMT}) or asctime ({@code Sun Nov  6 08:49:37 1994}), all GMT.
     */
    public static Optional<Duration> parseRetryAfter(String raw, Instant now) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (DELTA_SECONDS.matcher(value).matches()) {
            long seconds = value.length() > MAX_DELTA_DIGITS ? 9_999_999_999L : Long.parseLong(value);
            return seconds > 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
        }
        Optional<Instant> date = parseHttpDate(value);
        if (date.isEmpty() || !date.get().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, date.get()));
    }

    private static Optional<Instant> parseHttpDate(String value) {
        if (value.endsWith(" GMT")) {
            try {
                return Optional.of(ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (DateTimeParseException notImfFixdate) {
                // try the obsolete forms below
            }
            try {
                return Optional.of(LocalDateTime.parse(value, RFC_850).toInstant(ZoneOffset.UTC));
            } catch (DateTimeParseException notRfc850) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(LocalDateTime.parse(value, ASCTIME).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException notAsctime) {
            return Optional.empty();
        }
    }

    private Duration jittered(Duration base) {
        double ratio = properties.jitterRatio();
        if (ratio == 0.0) {
            return base;
        }
        RandomGenerator source = random != null ? random : ThreadLocalRandom.current();
        double factor = 1.0 + (source.nextDouble() * 2.0 - 1.0) * ratio; // [1 - r, 1 + r)
        return Duration.ofMillis(Math.round(base.toMillis() * factor));
    }

    private static RetryDecision dead(int attemptNumber, AttemptOutcome outcome, RetryDecision.DeadReason reason) {
        return new RetryDecision(RetryDecision.Kind.DEAD, attemptNumber, null, null, false, outcome.errorClass(), reason);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /** Configured backoff delays, for diagnostics. */
    public List<Duration> retryDelays() {
        return properties.retryDelays();
    }
}
