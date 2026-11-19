package com.swiftdrop.iam.service;

import org.springframework.dao.DataAccessException;
import com.swiftdrop.iam.exception.TooManyLoginAttemptsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Throttles password guessing with two Redis counters per 15-minute window:
 * <ul>
 *   <li><b>account + IP</b> (5 failures): the normal lockout a user can trip by mistyping.</li>
 *   <li><b>account across every IP</b> (20 failures): stops a distributed attack (credential stuffing
 *       from a botnet) that stays under the per-IP limit by rotating addresses.</li>
 * </ul>
 * The limiter fails open when Redis is unavailable so a cache outage cannot stop every login; the
 * one-time-code flows, which protect account recovery, fail closed instead.
 */
@Slf4j
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final int MAX_ACCOUNT_FAILURES = 20;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auth:login:fail:";
    private static final String ACCOUNT_KEY_PREFIX = "auth:login:fail:account:";

    private final StringRedisTemplate redisTemplate;
    private final TokenIssuanceService tokenIssuanceService;

    public LoginAttemptService(StringRedisTemplate redisTemplate, TokenIssuanceService tokenIssuanceService) {
        this.redisTemplate = redisTemplate;
        this.tokenIssuanceService = tokenIssuanceService;
    }

    public void assertAllowed(String identifier, String ipAddress) {
        try {
            if (count(key(identifier, ipAddress)) >= MAX_FAILURES
                    || count(accountKey(identifier)) >= MAX_ACCOUNT_FAILURES) {
                throw new TooManyLoginAttemptsException();
            }
        } catch (TooManyLoginAttemptsException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("Login attempt limiter unavailable: {}", exception.getMessage());
        }
    }

    public void recordFailure(String identifier, String ipAddress) {
        try {
            increment(key(identifier, ipAddress));
            increment(accountKey(identifier));
        } catch (DataAccessException exception) {
            log.warn("Unable to record login failure: {}", exception.getMessage());
        }
    }

    /**
     * Called after a successful login. Clears only this client's counter: the account-wide counter keeps
     * running, so an attacker spread over many IPs is not reset by the real owner signing in.
     */
    public void clear(String identifier, String ipAddress) {
        try {
            redisTemplate.delete(key(identifier, ipAddress));
        } catch (DataAccessException exception) {
            log.warn("Unable to clear login failures: {}", exception.getMessage());
        }
    }

    private long count(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    private void increment(String key) {
        Long failures = redisTemplate.opsForValue().increment(key);
        if (failures != null && failures == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    private String key(String identifier, String ipAddress) {
        String raw = normalize(identifier) + "|" + (ipAddress == null ? "unknown" : ipAddress);
        return KEY_PREFIX + tokenIssuanceService.hashToken(raw);
    }

    private String accountKey(String identifier) {
        return ACCOUNT_KEY_PREFIX + tokenIssuanceService.hashToken(normalize(identifier));
    }

    private static String normalize(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase();
    }
}
