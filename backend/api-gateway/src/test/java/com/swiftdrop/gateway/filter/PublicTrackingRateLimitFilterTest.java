package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

class PublicTrackingRateLimitFilterTest {

    private static final String CLIENT_IP = "203.0.113.10";
    private static final String OTHER_IP = "198.51.100.20";

    private CountingChain chain;
    private PublicTrackingRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return Mono.just(counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet());
        });
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        filter = new PublicTrackingRateLimitFilter(
                redisTemplate, new GatewayErrorWriter(objectMapper), 60, 3600);
        chain = new CountingChain();
    }

    @Test
    void sixtyFirstRequestFromSameIpIsRateLimited() {
        for (int request = 1; request <= 60; request++) {
            MockServerWebExchange exchange = request(trackingNumber(request), CLIENT_IP);

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
            assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                    .isEqualTo(Integer.toString(60 - request));
        }

        MockServerWebExchange limited = request("SD-0061-0000", CLIENT_IP);
        filter.filter(limited, chain).block();

        assertThat(limited.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(limited.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(chain.calls).isEqualTo(60);
    }

    @Test
    void anotherIpIsNotAffectedByTheFirstIp() {
        for (int request = 1; request <= 61; request++) {
            filter.filter(request(trackingNumber(request), CLIENT_IP), chain).block();
        }

        MockServerWebExchange other = request("SD-9999-0000", OTHER_IP);
        filter.filter(other, chain).block();

        assertThat(other.getResponse().getStatusCode()).isNull();
        assertThat(chain.calls).isEqualTo(61);
    }

    private MockServerWebExchange request(String trackingNumber, String clientIp) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/public/track/" + trackingNumber)
                        .header("X-Forwarded-For", clientIp)
                        .build());
    }

    private String trackingNumber(int sequence) {
        return String.format("SD-%04d-0000", sequence);
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
