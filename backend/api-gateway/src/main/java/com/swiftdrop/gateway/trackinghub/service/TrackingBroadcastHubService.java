package com.swiftdrop.gateway.trackinghub.service;

import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrackingBroadcastHubService {
    private final Map<String, Sinks.Many<PublicTrackingViewDto>> channels = new ConcurrentHashMap<>();
    private final Map<String, PublicTrackingViewDto> latestViews = new ConcurrentHashMap<>();

    public void publish(PublicTrackingViewDto view) {
        latestViews.put(view.trackingId(), view);
        channel(view.trackingId()).tryEmitNext(view);
    }

    public PublicTrackingViewDto current(String trackingId) {
        return latestViews.get(trackingId);
    }

    public Flux<PublicTrackingViewDto> stream(String trackingId) {
        return channel(trackingId).asFlux();
    }

    private Sinks.Many<PublicTrackingViewDto> channel(String trackingId) {
        return channels.computeIfAbsent(trackingId,
                ignored -> Sinks.many().multicast().directBestEffort());
    }
}
