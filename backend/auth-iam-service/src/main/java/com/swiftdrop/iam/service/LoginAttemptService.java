package com.swiftdrop.iam.service;

import com.swiftdrop.iam.exception.TooManyLoginAttemptsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auth:login:fail:";

    private final StringRedisTemplate redisTemplate;
    private final TokenIssuanceService tokenIssuanceService;

    public LoginAttemptService(StringRedisTemplate redisTemplate, TokenIssuanceService tokenIssuanceService) {
        this.redisTemplate = redisTemplate;
        this.tokenIssuanceService = tokenIssuanceService;
    }

    public void assertAllowed(String identifier, String ipAddress) {
        try {
            String value = redisTemplate.opsForValue().get(key(identifier, ipAddress));
            if (value != null && Long.parseLong(value) >= MAX_FAILURES) {
                throw new TooManyLoginAttemptsException();
            }
        } catch (TooManyLoginAttemptsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Login attempt limiter unavailable: {}", exception.getMessage());
        }
    }

    public void recordFailure(String identifier, String ipAddress) {
        try {
            String key = key(identifier, ipAddress);
            Long failures = redisTemplate.opsForValue().increment(key);
            if (failures != null && failures == 1L) {
                redisTemplate.expire(key, WINDOW);
            }
        } catch (RuntimeException exception) {
            log.warn("Unable to record login failure: {}", exception.getMessage());
        }
    }

    public void clear(String identifier, String ipAddress) {
        try {
            redisTemplate.delete(key(identifier, ipAddress));
        } catch (RuntimeException exception) {
            log.warn("Unable to clear login failures: {}", exception.getMessage());
        }
    }

    private String key(String identifier, String ipAddress) {
        String raw = (identifier == null ? "" : identifier.trim().toLowerCase())
                + "|" + (ipAddress == null ? "unknown" : ipAddress);
        return KEY_PREFIX + tokenIssuanceService.hashToken(raw);
    }
}
