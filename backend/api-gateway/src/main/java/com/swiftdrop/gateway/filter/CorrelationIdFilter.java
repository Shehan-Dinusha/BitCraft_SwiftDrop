package com.swiftdrop.gateway.filter;

import com.swiftdrop.contracts.security.JwtHeaderConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that ensures every request carries a unique correlation ID
 * for end-to-end distributed tracing across all SwiftDrop services.
 *
 * <p>If the incoming request already has an {@code X-Correlation-Id} header,
 * it is preserved. Otherwise, a new UUID is generated and injected.</p>
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String existingCorrelationId = exchange.getRequest()
                .getHeaders()
                .getFirst(JwtHeaderConstants.X_CORRELATION_ID);

        String correlationId = (existingCorrelationId != null && !existingCorrelationId.isBlank())
                ? existingCorrelationId
                : UUID.randomUUID().toString();

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(JwtHeaderConstants.X_CORRELATION_ID, correlationId)
                .build();

        // Also add correlation ID to response headers for client traceability
        exchange.getResponse().getHeaders()
                .add(JwtHeaderConstants.X_CORRELATION_ID, correlationId);

        log.debug("Correlation ID [{}] assigned to {} {}",
                correlationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath());

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -200; // Run before JWT filter to ensure correlation ID is available for logging
    }
}
