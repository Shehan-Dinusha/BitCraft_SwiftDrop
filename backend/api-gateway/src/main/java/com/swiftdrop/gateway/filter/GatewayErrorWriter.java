package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.dto.ApiErrorResponse;
import com.swiftdrop.contracts.security.JwtHeaderConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Writes consistent JSON error bodies from global filters without leaking
 * exception details to external clients.
 */
@Component
public class GatewayErrorWriter {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWriter.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(JwtHeaderConstants.X_CORRELATION_ID);

        ApiErrorResponse error = ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getURI().getPath(),
                correlationId
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(error);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            log.error("Unable to serialize gateway error response", exception);
            byte[] fallback = "{\"error\":\"Internal Server Error\"}"
                    .getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(fallback);
            return response.writeWith(Mono.just(buffer));
        }
    }
}
