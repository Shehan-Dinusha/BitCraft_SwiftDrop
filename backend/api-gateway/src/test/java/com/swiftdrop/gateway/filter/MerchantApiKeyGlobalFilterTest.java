package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class MerchantApiKeyGlobalFilterTest {

    private MerchantApiKeyGlobalFilter filter;
    private RecordingChain chain;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        filter = new MerchantApiKeyGlobalFilter(
                WebClient.builder(),
                mock(ReactiveStringRedisTemplate.class),
                new GatewayErrorWriter(objectMapper),
                "http://localhost:1/internal/v1/api-keys/validate",
                60);
        chain = new RecordingChain();
    }

    @Test
    void missingApiKeyReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/merchant-order-ingest/orders").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    @Test
    void nonMerchantPathSkipsApiKeyValidation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123").build());

        filter.filter(exchange, chain).block();

        assertNotNull(chain.exchange.get());
    }

    private static final class RecordingChain implements GatewayFilterChain {

        private final AtomicReference<ServerWebExchange> exchange = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.exchange.set(exchange);
            return Mono.empty();
        }
    }
}
