package com.swiftdrop.gateway.trackinghub.service;

import com.swiftdrop.gateway.trackinghub.client.OrderTrackingClient;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Makes sure public tracking views include the city and masked sender/receiver
 * details owned by the order service. The gateway cache is populated by delivery
 * events, which never carry contact data, so both the REST request path and the
 * SSE/WebSocket subscription path enrich the cache before serving a view.
 */
@Service
@RequiredArgsConstructor
public class TrackingViewEnrichmentService {

    private final TrackingBroadcastHubService broadcastHub;
    private final OrderTrackingClient orderClient;
    private final TrackingDataSanitizerProxy sanitizer;

    /**
     * Returns the cached view enriched with masked contact details when needed.
     * A failed or missing order-service lookup never turns an existing cached
     * view into a miss.
     */
    public Mono<Optional<PublicTrackingViewDto>> enrich(String trackingNumber) {
        PublicTrackingViewDto current = broadcastHub.current(trackingNumber);
        if (current != null && !sanitizer.needsContactDetails(current)) {
            return Mono.just(Optional.of(current));
        }
        return orderClient.getTracking(trackingNumber)
                .map(optional -> optional.map(internal -> {
                    PublicTrackingViewDto enriched = sanitizer.mergeContactDetails(current, internal);
                    broadcastHub.updateCache(trackingNumber, enriched);
                    return enriched;
                }))
                .defaultIfEmpty(Optional.empty())
                .map(enriched -> enriched.or(() -> Optional.ofNullable(current)));
    }
}
