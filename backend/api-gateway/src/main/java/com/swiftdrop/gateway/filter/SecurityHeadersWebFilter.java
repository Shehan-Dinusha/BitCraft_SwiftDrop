package com.swiftdrop.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Adds standard browser security headers to every gateway response without
 * overwriting values already supplied by a downstream service.
 */
@Component
public class SecurityHeadersWebFilter implements WebFilter, Ordered {

    static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    static final String FRAME_OPTIONS = "X-Frame-Options";
    static final String REFERRER_POLICY = "Referrer-Policy";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            setIfAbsent(headers, CONTENT_TYPE_OPTIONS, "nosniff");
            setIfAbsent(headers, FRAME_OPTIONS, "DENY");
            setIfAbsent(headers, REFERRER_POLICY, "no-referrer");
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void setIfAbsent(HttpHeaders headers, String name, String value) {
        if (!headers.containsKey(name)) {
            headers.set(name, value);
        }
    }
}
