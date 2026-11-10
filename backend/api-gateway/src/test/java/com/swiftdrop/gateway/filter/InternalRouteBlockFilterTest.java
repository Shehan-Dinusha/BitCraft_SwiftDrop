package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class InternalRouteBlockFilterTest {

    private InternalRouteBlockFilter filter;
    private RecordingChain chain;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        filter = new InternalRouteBlockFilter(new GatewayErrorWriter(objectMapper));
        chain = new RecordingChain();
    }

    @Test
    void blocksDirectInternalPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/v1/api-keys/validate").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    @Test
    void blocksInternalPathHiddenBehindARoute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/internal/sync").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertNull(chain.exchange.get());
    }

    @Test
    void allowsRegularPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123").build());

        filter.filter(exchange, chain).block();

        assertNotNull(chain.exchange.get());
    }

    private static final class RecordingChain implements WebFilterChain {

        private final AtomicReference<ServerWebExchange> exchange = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.exchange.set(exchange);
            return Mono.empty();
        }
    }
}
