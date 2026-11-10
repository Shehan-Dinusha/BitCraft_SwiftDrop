package com.swiftdrop.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityHeadersWebFilterTest {

    private final WebTestClient client = WebTestClient
            .bindToWebHandler(exchange -> exchange.getResponse().setComplete())
            .webFilter(new SecurityHeadersWebFilter())
            .build();

    @Test
    void addsSecurityHeadersToEveryResponse() {
        client.get().uri("/api/v1/orders/123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(SecurityHeadersWebFilter.CONTENT_TYPE_OPTIONS, "nosniff")
                .expectHeader().valueEquals(SecurityHeadersWebFilter.FRAME_OPTIONS, "DENY")
                .expectHeader().valueEquals(SecurityHeadersWebFilter.REFERRER_POLICY, "no-referrer");
    }

    @Test
    void keepsHeadersSetByDownstreamService() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/123").build());

        new SecurityHeadersWebFilter()
                .filter(exchange, current -> {
                    current.getResponse().getHeaders()
                            .set(SecurityHeadersWebFilter.FRAME_OPTIONS, "SAMEORIGIN");
                    return current.getResponse().setComplete();
                })
                .block();

        assertEquals("SAMEORIGIN",
                exchange.getResponse().getHeaders().getFirst(SecurityHeadersWebFilter.FRAME_OPTIONS));
    }
}
