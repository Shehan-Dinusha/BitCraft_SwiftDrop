package com.swiftdrop.iam.service;

import com.swiftdrop.iam.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private TokenIssuanceService tokenIssuanceService;

    /** A tiny in-memory Redis for the keys the service touches. */
    private final Map<String, Long> store = new HashMap<>();
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(tokenIssuanceService.hashToken(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(valueOps.get(anyString())).thenAnswer(invocation -> {
            Long value = store.get(invocation.<String>getArgument(0));
            return value == null ? null : value.toString();
        });
        when(valueOps.increment(anyString())).thenAnswer(invocation -> store.merge(invocation.getArgument(0), 1L, Long::sum));
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> store.remove(invocation.<String>getArgument(0)) != null);
        service = new LoginAttemptService(redisTemplate, tokenIssuanceService);
    }

    @Test
    void fiveFailuresFromOneAddressLockThatAddress() {
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.recordFailure("alice", "198.51.100.1");
        }

        assertThatThrownBy(() -> service.assertAllowed("alice", "198.51.100.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> service.assertAllowed("alice", "198.51.100.2")).doesNotThrowAnyException();
    }

    @Test
    void failuresSpreadOverManyAddressesStillLockTheAccount() {
        // A botnet stays under the per-address limit by using a new address for every guess.
        for (int i = 0; i < LoginAttemptService.MAX_ACCOUNT_FAILURES; i++) {
            service.recordFailure("Alice", "203.0.113." + i);
        }

        assertThatThrownBy(() -> service.assertAllowed("alice", "192.0.2.99"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> service.assertAllowed("bob", "192.0.2.99")).doesNotThrowAnyException();
    }

    @Test
    void successClearsOnlyThisClientsCounter() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("alice", "198.51.100.1");
        }

        service.clear("alice", "198.51.100.1");

        assertThatCode(() -> service.assertAllowed("alice", "198.51.100.1")).doesNotThrowAnyException();
        assertThat(store).containsEntry("auth:login:fail:account:alice", 4L);
    }

    @Test
    void countersExpireAfterTheWindow() {
        service.recordFailure("alice", "198.51.100.1");

        verify(redisTemplate).expire(eq("auth:login:fail:alice|198.51.100.1"), eq(Duration.ofMinutes(15)));
        verify(redisTemplate).expire(eq("auth:login:fail:account:alice"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void redisOutageFailsOpen() {
        when(valueOps.get(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> service.assertAllowed("alice", "198.51.100.1")).doesNotThrowAnyException();
    }
}
