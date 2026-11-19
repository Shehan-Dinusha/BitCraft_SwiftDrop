package com.swiftdrop.iam.service;

import org.springframework.dao.DataAccessException;
import org.springframework.amqp.AmqpException;
import com.swiftdrop.contracts.events.auth.AuthOtpRequestedEvent;
import com.swiftdrop.contracts.events.auth.OtpPurpose;
import com.swiftdrop.iam.config.OtpProperties;
import com.swiftdrop.iam.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Issues and verifies 6-digit one-time codes (email verification, password reset).
 *
 * <p>Security properties:
 * <ul>
 *   <li>Codes come from {@link SecureRandom} and are stored only as a salted SHA-256 hash in Redis,
 *       with a TTL, so they vanish on their own and are never in PostgreSQL.</li>
 *   <li>Single use: a successful check deletes the code.</li>
 *   <li>Guessing is bounded: after {@code maxAttempts} wrong tries the code is destroyed.</li>
 *   <li>Sending is bounded per user/purpose (cooldown and hourly cap) and per client IP.</li>
 *   <li>Fails closed: if Redis is unavailable no code is issued or accepted.</li>
 * </ul>
 */
@Slf4j
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CODE_FORMAT = Pattern.compile("^\\d{6}$");
    private static final String PREFIX = "auth:otp:";

    private final StringRedisTemplate redis;
    private final OtpProperties props;
    private final AuthOtpPublisher publisher;

    public OtpService(StringRedisTemplate redis, OtpProperties props, AuthOtpPublisher publisher) {
        this.redis = redis;
        this.props = props;
        this.publisher = publisher;
    }

    /** Generic failure used for every kind of wrong, expired, or missing code. */
    public static AuthException invalidCode() {
        return new AuthException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
    }

    /**
     * Caps how many OTP-related requests a single client IP can make per hour. Fails open on Redis
     * errors (the per-user limits below still apply), since this is a coarse abuse guard.
     */
    public void assertIpAllowed(String ipAddress) {
        try {
            String key = PREFIX + "ip:" + sha256(ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofHours(1));
            }
            if (count != null && count > props.getIpMaxRequestsPerHour()) {
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later.");
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.warn("OTP IP limiter unavailable: {}", ex.getMessage());
        }
    }

    /**
     * Creates a new code for the user, replacing any earlier one, and asks notification-service to
     * email it.
     *
     * @throws AuthException 429 when the cooldown or hourly cap is hit; 503 when Redis is unavailable.
     */
    public void issue(OtpPurpose purpose, UUID userId) {
        try {
            Boolean mayStart = redis.opsForValue().setIfAbsent(key("cooldown", purpose, userId), "1",
                    Duration.ofSeconds(props.getResendCooldownSeconds()));
            if (!Boolean.TRUE.equals(mayStart)) {
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait a moment before requesting another code.");
            }
            String hourlyKey = key("hourly", purpose, userId);
            Long sent = redis.opsForValue().increment(hourlyKey);
            if (sent != null && sent == 1L) {
                redis.expire(hourlyKey, Duration.ofHours(1));
            }
            if (sent != null && sent > props.getMaxSendsPerHour()) {
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many codes requested. Try again in an hour.");
            }

            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            redis.opsForValue().set(key("code", purpose, userId), hash(purpose, userId, code),
                    Duration.ofMinutes(props.getTtlMinutes()));
            redis.delete(key("attempts", purpose, userId));

            publisher.publish(new AuthOtpRequestedEvent(null, null, userId, purpose, code,
                    props.getTtlMinutes(), null));
            if (props.isLogCode()) {
                log.warn("[DEV ONLY] {} code for user {}: {}", purpose, userId, code);
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (DataAccessException | AmqpException ex) {
            log.error("Could not issue {} code: {}", purpose, ex.getMessage());
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Verification is temporarily unavailable. Please try again shortly.");
        }
    }

    /** Checks a submitted code and, on success, consumes it. */
    public void verify(OtpPurpose purpose, UUID userId, String submittedCode) {
        check(purpose, userId, submittedCode);
        consume(purpose, userId);
    }

    /**
     * Checks a submitted code without consuming it, so a caller can run further validation (for
     * example "new password must differ") before the code is spent. Every call counts as an attempt.
     *
     * @throws AuthException 400 for a wrong/expired code; 429 once attempts are exhausted;
     *                       503 when Redis is unavailable.
     */
    public void check(OtpPurpose purpose, UUID userId, String submittedCode) {
        if (submittedCode == null || !CODE_FORMAT.matcher(submittedCode).matches()) {
            throw invalidCode();
        }
        try {
            String codeKey = key("code", purpose, userId);
            String stored = redis.opsForValue().get(codeKey);
            if (stored == null) {
                throw invalidCode();
            }

            String attemptsKey = key("attempts", purpose, userId);
            Long attempts = redis.opsForValue().increment(attemptsKey);
            if (attempts != null && attempts == 1L) {
                redis.expire(attemptsKey, Duration.ofMinutes(props.getTtlMinutes()));
            }
            if (attempts != null && attempts > props.getMaxAttempts()) {
                redis.delete(codeKey);
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many incorrect attempts. Request a new code.");
            }

            boolean matches = MessageDigest.isEqual(
                    stored.getBytes(StandardCharsets.UTF_8),
                    hash(purpose, userId, submittedCode).getBytes(StandardCharsets.UTF_8));
            if (!matches) {
                throw invalidCode();
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Could not verify {} code: {}", purpose, ex.getMessage());
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Verification is temporarily unavailable. Please try again shortly.");
        }
    }

    /**
     * Spends a code that {@link #check} accepted. The delete is atomic, so if two requests race with
     * the same code only one of them succeeds.
     *
     * @throws AuthException 400 when the code was already used; 503 when Redis is unavailable.
     */
    public void consume(OtpPurpose purpose, UUID userId) {
        boolean deleted;
        try {
            deleted = Boolean.TRUE.equals(redis.delete(key("code", purpose, userId)));
            redis.delete(key("attempts", purpose, userId));
        } catch (DataAccessException ex) {
            log.error("Could not consume {} code: {}", purpose, ex.getMessage());
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Verification is temporarily unavailable. Please try again shortly.");
        }
        if (!deleted) {
            throw invalidCode();
        }
    }

    private static String key(String kind, OtpPurpose purpose, UUID userId) {
        return PREFIX + kind + ":" + purpose.name().toLowerCase() + ":" + userId;
    }

    private static String hash(OtpPurpose purpose, UUID userId, String code) {
        return sha256(purpose.name() + ":" + userId + ":" + code);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
