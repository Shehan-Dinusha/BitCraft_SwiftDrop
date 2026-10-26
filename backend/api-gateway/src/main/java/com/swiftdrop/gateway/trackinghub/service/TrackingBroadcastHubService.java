package com.swiftdrop.gateway.trackinghub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.ParcelFeeRevisedEvent;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory read model and fan-out hub for public tracking views and private
 * STOMP topics. The view itself is always produced by
 * {@link TrackingDataSanitizerProxy}; this service never accepts unsanitized
 * event payloads directly.
 */
@Service
public class TrackingBroadcastHubService {

    private static final int MAX_PROCESSED_EVENTS = 100_000;

    private final Map<String, Sinks.Many<PublicTrackingViewDto>> trackChannels = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<String>> topicChannels = new ConcurrentHashMap<>();
    private final Map<String, PublicTrackingViewDto> latestViews = new ConcurrentHashMap<>();
    private final Map<String, UUID> customerIds = new ConcurrentHashMap<>();
    private final Set<UUID> processedEvents = ConcurrentHashMap.newKeySet();

    private final TrackingDataSanitizerProxy sanitizer;
    private final ObjectMapper objectMapper;

    public TrackingBroadcastHubService(TrackingDataSanitizerProxy sanitizer, ObjectMapper objectMapper) {
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
    }

    public void onStatusChanged(ParcelStatusChangedEvent event) {
        if (event == null || event.trackingNumber() == null || event.trackingNumber().isBlank()) {
            return;
        }
        if (isDuplicate(event.eventId())) {
            return;
        }

        String trackingNumber = event.trackingNumber();
        PublicTrackingViewDto current = latestViews.get(trackingNumber);
        PublicTrackingViewDto view = sanitizer.append(current, event);
        latestViews.put(trackingNumber, view);

        UUID customerId = event.customerId();
        if (customerId != null) {
            customerIds.put(trackingNumber, customerId);
        } else {
            customerId = customerIds.get(trackingNumber);
        }

        emitView(view, customerId);
    }

    public void onLegacyStateChanged(DeliveryStateChangedEvent event) {
        if (event == null || event.deliveryId() == null) {
            return;
        }
        if (isDuplicate(event.eventId())) {
            return;
        }

        String trackingNumber = event.deliveryId().toString();
        PublicTrackingViewDto current = latestViews.get(trackingNumber);
        PublicTrackingViewDto view = sanitizer.appendLegacy(current, event);
        latestViews.put(trackingNumber, view);
        emitView(view, customerIds.get(trackingNumber));
    }

    public void onFeeRevised(ParcelFeeRevisedEvent event) {
        if (event == null) {
            return;
        }
        if (isDuplicate(event.eventId())) {
            return;
        }

        UUID customerId = event.customerId();
        if (customerId == null && event.trackingNumber() != null) {
            customerId = customerIds.get(event.trackingNumber());
        }
        if (customerId == null) {
            return;
        }

        emitJson("/topic/customer/" + customerId, event);
    }

    public PublicTrackingViewDto current(String trackingNumber) {
        return trackingNumber == null ? null : latestViews.get(trackingNumber);
    }

    public Flux<PublicTrackingViewDto> stream(String trackingNumber) {
        PublicTrackingViewDto current = latestViews.get(trackingNumber);
        Flux<PublicTrackingViewDto> updates = trackChannel(trackingNumber).asFlux();
        return current == null ? updates : Flux.concat(Flux.just(current), updates);
    }

    public Flux<String> streamTopic(String topic) {
        return topicChannel(topic).asFlux();
    }

    private void emitView(PublicTrackingViewDto view, UUID customerId) {
        trackChannel(view.trackingNumber()).tryEmitNext(view);

        String json = toJson(view);
        if (json == null) {
            return;
        }
        topicChannel("/topic/track/" + view.trackingNumber()).tryEmitNext(json);
        if (customerId != null) {
            topicChannel("/topic/customer/" + customerId).tryEmitNext(json);
        }
    }

    private void emitJson(String topic, Object payload) {
        String json = toJson(payload);
        if (json != null) {
            topicChannel(topic).tryEmitNext(json);
        }
    }

    private Sinks.Many<PublicTrackingViewDto> trackChannel(String trackingNumber) {
        return trackChannels.computeIfAbsent(trackingNumber,
                ignored -> Sinks.many().multicast().directBestEffort());
    }

    private Sinks.Many<String> topicChannel(String topic) {
        return topicChannels.computeIfAbsent(topic,
                ignored -> Sinks.many().multicast().directBestEffort());
    }

    private boolean isDuplicate(UUID eventId) {
        if (eventId == null) {
            return false;
        }
        if (processedEvents.size() > MAX_PROCESSED_EVENTS) {
            processedEvents.clear();
        }
        return !processedEvents.add(eventId);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
}
