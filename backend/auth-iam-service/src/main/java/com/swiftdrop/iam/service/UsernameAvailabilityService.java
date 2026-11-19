package com.swiftdrop.iam.service;

import org.springframework.dao.DataAccessException;
import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.UsernameAvailabilityResponse;
import com.swiftdrop.iam.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Live "is this username free?" check for the sign-up form. Usernames are public handles, and
 * {@code /register} already answers 409 for a taken one, so this reveals nothing new; it only moves the
 * answer to while the user is typing. It is still rate limited per client IP so it cannot be used to
 * bulk-harvest usernames. Uniqueness itself is enforced by the database (unique index on
 * {@code lower(username)}), which this check never replaces.
 */
@Slf4j
@Service
public class UsernameAvailabilityService {

    /** Same rule as {@code RegisterUserRequest.username}. */
    static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");
    static final int MAX_CHECKS_PER_MINUTE = 60;
    private static final String KEY_PREFIX = "auth:username-check:";

    private final UserAccountRepository userAccountRepository;
    private final StringRedisTemplate redisTemplate;
    private final TokenIssuanceService tokenIssuanceService;

    public UsernameAvailabilityService(UserAccountRepository userAccountRepository,
                                       StringRedisTemplate redisTemplate,
                                       TokenIssuanceService tokenIssuanceService) {
        this.userAccountRepository = userAccountRepository;
        this.redisTemplate = redisTemplate;
        this.tokenIssuanceService = tokenIssuanceService;
    }

    @Transactional(readOnly = true)
    public UsernameAvailabilityResponse check(String rawUsername, String ipAddress) {
        assertWithinLimit(ipAddress);
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (!USERNAME.matcher(username).matches()) {
            return new UsernameAvailabilityResponse(username, false, UsernameAvailabilityResponse.INVALID);
        }
        // Case-insensitive, and counts never-verified sign-ups too: a pending account holds its name
        // until it is verified or cleaned up, exactly as /register enforces.
        boolean taken = userAccountRepository.existsByUsernameIgnoreCase(username);
        return new UsernameAvailabilityResponse(username, !taken, taken ? UsernameAvailabilityResponse.TAKEN : null);
    }

    private void assertWithinLimit(String ipAddress) {
        long count;
        try {
            String key = KEY_PREFIX + tokenIssuanceService.hashToken(ipAddress == null ? "unknown" : ipAddress);
            Long current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            count = current == null ? 0 : current;
        } catch (DataAccessException exception) {
            // A convenience check: fail open rather than break the sign-up form when Redis is down.
            log.warn("Username availability limiter unavailable: {}", exception.getMessage());
            return;
        }
        if (count > MAX_CHECKS_PER_MINUTE) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again in a minute.");
        }
    }
}
