package com.swiftdrop.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed fixed-window rate limiter for unauthenticated public tracking
 * endpoints. The limiter fails open when Redis is unavailable so a cache outage
 * cannot take down parcel tracking.
 */
@Slf4j
@Component
public class PublicTrackingRateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayErrorWriter errorWriter;
    private final long limit;
    private final long windowSeconds;

    public PublicTrackingRateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            GatewayErrorWriter errorWriter,
            @Value("${swiftdrop.rate-limit.public-tracking.limit:60}") long limit,
            @Value("${swiftdrop.rate-limit.public-tracking.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.errorWriter = errorWriter;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() != HttpMethod.GET || !isPublicTrackingPath(request.getURI().getPath())) {
            return chain.filter(exchange);
        }

        long epochSecond = Instant.now().getEpochSecond();
        long window = epochSecond / windowSeconds;
        String key = "rate:public-track:" + clientIp(request) + ":" + window;

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    Mono<Boolean> expiry = count == 1
                            ? redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                            : Mono.just(true);
                    return expiry.then(applyLimit(exchange, chain, count, epochSecond));
                })
                .onErrorResume(exception -> {
                    log.warn("Public tracking rate limiter unavailable: {}", exception.getMessage());
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> applyLimit(ServerWebExchange exchange,
                                  GatewayFilterChain chain,
                                  long count,
                                  long epochSecond) {
        var responseHeaders = exchange.getResponse().getHeaders();
        responseHeaders.set("X-RateLimit-Limit", Long.toString(limit));
        responseHeaders.set("X-RateLimit-Remaining", Long.toString(Math.max(0, limit - count)));

        if (count <= limit) {
            return chain.filter(exchange);
        }

        long retryAfter = windowSeconds - (epochSecond % windowSeconds);
        responseHeaders.set("Retry-After", Long.toString(retryAfter));
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                "Too many tracking requests. Please retry later.");
    }

    @Override
    public int getOrder() {
        return -180;
    }

    private boolean isPublicTrackingPath(String path) {
        return path.equals("/api/v1/tracking")
                || path.startsWith("/api/v1/tracking/")
                || path.startsWith("/api/v1/public/track")
                || path.startsWith("/api/v1/public/tracking");
    }

    private String clientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
