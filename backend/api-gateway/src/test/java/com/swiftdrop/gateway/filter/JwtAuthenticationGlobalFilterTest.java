package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.security.JwtSessionRevocation;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationGlobalFilterTest {
    private static final String SECRET = "gateway-regression-key-at-least-32-bytes-long!!";
    private static final String PREFIX = "jwt:regression:";
    private static final String USER = UUID.randomUUID().toString();

    @Mock private ReactiveStringRedisTemplate redis;
    @Mock private ReactiveValueOperations<String, String> values;
    private JwtAuthenticationGlobalFilter filter;
    private Instant second;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationGlobalFilter(SECRET, PREFIX, redis,
                new GatewayErrorWriter(new ObjectMapper().findAndRegisterModules()));
        second = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        when(redis.hasKey(anyString())).thenReturn(Mono.just(false));
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void rejectsPreChangeToken() {
        when(values.get(PREFIX + "user:" + USER)).thenReturn(Mono.just(second.plusMillis(500).toString()));
        assertStatus(token(second.plusMillis(100), true), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void permitsFreshLoginInTheSameSecondAsThePasswordChange() {
        when(values.get(PREFIX + "user:" + USER)).thenReturn(Mono.just(second.plusMillis(500).toString()));
        assertStatus(token(second.plusMillis(900), true), HttpStatus.OK);
    }

    @Test
    void rejectsExistingTokensUsingStandardIat() {
        when(values.get(PREFIX + "user:" + USER)).thenReturn(Mono.just(second.plusMillis(500).toString()));
        assertStatus(token(second.plusMillis(900), false), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void honorsLegacyUserBlacklistMarkers() {
        when(values.get(PREFIX + "user:" + USER)).thenReturn(Mono.just("1"));
        assertStatus(token(second, true), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void permitsTokensWithoutAUserRevocation() {
        when(values.get(PREFIX + "user:" + USER)).thenReturn(Mono.empty());
        assertStatus(token(second, true), HttpStatus.OK);
    }

    @Test
    void stillRejectsAnIndividuallyLoggedOutToken() {
        when(values.get(PREFIX + "user:" + USER)).thenReturn(Mono.empty());
        when(redis.hasKey(PREFIX + "jti:regression-jti")).thenReturn(Mono.just(true));
        assertStatus(token(second, true), HttpStatus.UNAUTHORIZED);
    }

    private String token(Instant issuedAt, boolean preciseTime) {
        var builder = Jwts.builder().subject(USER).id("regression-jti")
                .issuedAt(Date.from(issuedAt)).expiration(Date.from(Instant.now().plusSeconds(3600)))
                .claim("role", "CUSTOMER");
        if (preciseTime) builder.claim(JwtSessionRevocation.ISSUED_AT_CLAIM, issuedAt.toString());
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private void assertStatus(String token, HttpStatus expected) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token));
        filter.filter(exchange, downstream -> {
            downstream.getResponse().setStatusCode(HttpStatus.OK);
            return downstream.getResponse().setComplete();
        }).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expected);
    }
}
