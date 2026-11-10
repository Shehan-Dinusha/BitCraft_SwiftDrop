package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingScrapingTest {

    @Test
    void sequentialGuessedTrackingNumbersAreThrottled() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return Mono.just(counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet());
        });
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        PublicTrackingRateLimitFilter filter = new PublicTrackingRateLimitFilter(
                redisTemplate,
                new GatewayErrorWriter(new ObjectMapper().findAndRegisterModules()),
                60,
                3600);
        CountingChain chain = new CountingChain();

        int limited = 0;
        for (int guess = 1; guess <= 100; guess++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get(String.format("/api/v1/public/track/SD-%04d-GUESS", guess))
                            .header("X-Forwarded-For", "203.0.113.99")
                            .build());

            filter.filter(exchange, chain).block();

            if (HttpStatus.TOO_MANY_REQUESTS.equals(exchange.getResponse().getStatusCode())) {
                limited++;
            }
        }

        assertThat(chain.calls).isEqualTo(60);
        assertThat(limited).isEqualTo(40);
    }

    private static final class CountingChain implements WebFilterChain {

        private int calls;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            calls++;
            return Mono.empty();
        }
    }
}
