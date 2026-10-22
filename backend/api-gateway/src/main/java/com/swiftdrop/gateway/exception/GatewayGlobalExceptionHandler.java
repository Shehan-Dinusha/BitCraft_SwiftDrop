package com.swiftdrop.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Global exception handler for the reactive gateway.
 * Catches unhandled exceptions and returns a structured JSON error response.
 */
@Component
@Order(-1) // Execute before Spring's default error handler
public class GatewayGlobalExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayGlobalExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GatewayGlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @NonNull
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = determineHttpStatus(ex);
        String path = exchange.getRequest().getURI().getPath();

        log.error("Gateway error on {} {}: {} ({})",
                exchange.getRequest().getMethod(), path,
                ex.getMessage(), status.value());

        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                path
        );

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            byte[] fallback = "{\"error\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(fallback);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }

    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatus.valueOf(rse.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
