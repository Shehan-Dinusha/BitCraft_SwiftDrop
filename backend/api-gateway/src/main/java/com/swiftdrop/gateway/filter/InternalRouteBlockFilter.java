package com.swiftdrop.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Blocks any external request that targets a service's {@code /internal/}
 * endpoints. This runs as a {@link WebFilter} (not a gateway
 * {@code GlobalFilter}) so it also covers paths that do not match a route.
 */
@Slf4j
@Component
public class InternalRouteBlockFilter implements WebFilter, Ordered {

    private final GatewayErrorWriter errorWriter;

    public InternalRouteBlockFilter(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isInternalPath(path)) {
            log.warn("Blocked external request to internal path: {}", path);
            return errorWriter.write(exchange, HttpStatus.NOT_FOUND, "Resource not found");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean isInternalPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.equals("/internal")
                || normalized.endsWith("/internal")
                || normalized.contains("/internal/");
    }
}
