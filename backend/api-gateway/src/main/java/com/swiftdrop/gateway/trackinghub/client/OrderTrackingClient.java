package com.swiftdrop.gateway.trackinghub.client;

import com.swiftdrop.contracts.dto.ApiResponse;
import com.swiftdrop.contracts.dto.tracking.InternalTrackingView;
import com.swiftdrop.gateway.config.GatewayRouteProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTrackingClient {

    private final WebClient webClient;
    private final GatewayRouteProperties routes;

    public Mono<Optional<InternalTrackingView>> getTracking(String trackingNumber) {
        return webClient.get()
                .uri(trackingUrl(trackingNumber))
                .retrieve()
                .onStatus(s -> s == HttpStatus.NOT_FOUND, resp -> Mono.error(new NotFoundException()))
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<InternalTrackingView>>() {})
                .map(ApiResponse::data)
                .map(Optional::ofNullable)
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(NotFoundException.class, e -> Mono.just(Optional.<InternalTrackingView>empty()))
                .onErrorResume(e -> {
                    log.warn("Order tracking lookup failed for {}: {}", trackingNumber, e.getMessage());
                    return Mono.just(Optional.<InternalTrackingView>empty());
                });
    }

    private String trackingUrl(String trackingNumber) {
        String base = routes.order();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/internal/v1/orders/tracking/" + trackingNumber;
    }

    private static class NotFoundException extends RuntimeException {
    }
}