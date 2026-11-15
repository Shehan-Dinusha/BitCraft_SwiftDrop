package com.swiftdrop.iam.service;

import com.swiftdrop.iam.domain.repository.UserAccountRepository;
import com.swiftdrop.iam.dto.UsernameAvailabilityResponse;
import com.swiftdrop.iam.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsernameAvailabilityServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private TokenIssuanceService tokenIssuanceService;

    private UsernameAvailabilityService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(tokenIssuanceService.hashToken(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(valueOps.increment(anyString())).thenReturn(1L);
        service = new UsernameAvailabilityService(userAccountRepository, redisTemplate, tokenIssuanceService);
    }

    @Test
    void freeUsernameIsAvailable() {
        when(userAccountRepository.existsByUsernameIgnoreCase("nimal.perera")).thenReturn(false);

        UsernameAvailabilityResponse response = service.check("  nimal.perera ", "198.51.100.1");

        assertThat(response.available()).isTrue();
        assertThat(response.username()).isEqualTo("nimal.perera");
        assertThat(response.reason()).isNull();
    }

    @Test
    void takenUsernameIsReportedRegardlessOfCase() {
        when(userAccountRepository.existsByUsernameIgnoreCase("Nimal.Perera")).thenReturn(true);

        UsernameAvailabilityResponse response = service.check("Nimal.Perera", "198.51.100.1");

        assertThat(response.available()).isFalse();
        assertThat(response.reason()).isEqualTo(UsernameAvailabilityResponse.TAKEN);
    }

    @Test
    void invalidUsernameIsRejectedWithoutTouchingTheDatabase() {
        assertThat(service.check("no", "198.51.100.1").reason()).isEqualTo(UsernameAvailabilityResponse.INVALID);
        assertThat(service.check("has space", "198.51.100.1").available()).isFalse();
        assertThat(service.check("a@b.com", "198.51.100.1").available()).isFalse();
        assertThat(service.check("x".repeat(51), "198.51.100.1").available()).isFalse();

        verify(userAccountRepository, never()).existsByUsernameIgnoreCase(anyString());
    }

    @Test
    void tooManyChecksFromOneAddressAreRefused() {
        when(valueOps.increment(anyString())).thenReturn((long) UsernameAvailabilityService.MAX_CHECKS_PER_MINUTE + 1);

        assertThatThrownBy(() -> service.check("nimal.perera", "198.51.100.1"))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void redisOutageFailsOpen() {
        when(valueOps.increment(anyString())).thenThrow(new IllegalStateException("redis down"));
        when(userAccountRepository.existsByUsernameIgnoreCase("nimal.perera")).thenReturn(false);

        assertThat(service.check("nimal.perera", "198.51.100.1").available()).isTrue();
    }
}
