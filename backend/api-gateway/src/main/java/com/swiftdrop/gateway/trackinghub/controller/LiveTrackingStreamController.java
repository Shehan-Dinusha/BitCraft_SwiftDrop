package com.swiftdrop.gateway.trackinghub.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import com.swiftdrop.gateway.trackinghub.service.TrackingViewEnrichmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Controller
@RequestMapping({"/api/v1/public/track", "/api/v1/tracking"})
@Tag(name = "Real-Time Tracking Hub", description = "SSE and WebSocket parcel update streams")
public class LiveTrackingStreamController implements WebSocketHandler {

    static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final TrackingBroadcastHubService broadcastHub;
    private final ObjectMapper objectMapper;
    private final TrackingViewEnrichmentService enrichmentService;
    private final Duration heartbeatInterval;

    @Autowired
    public LiveTrackingStreamController(TrackingBroadcastHubService broadcastHub,
                                        ObjectMapper objectMapper,
                                        TrackingViewEnrichmentService enrichmentService) {
        this(broadcastHub, objectMapper, enrichmentService, DEFAULT_HEARTBEAT_INTERVAL);
    }

    LiveTrackingStreamController(TrackingBroadcastHubService broadcastHub,
                                 ObjectMapper objectMapper,
                                 TrackingViewEnrichmentService enrichmentService,
                                 Duration heartbeatInterval) {
        this.broadcastHub = broadcastHub;
        this.objectMapper = objectMapper;
        this.enrichmentService = enrichmentService;
        this.heartbeatInterval = heartbeatInterval;
    }

    @GetMapping(value = "/{trackingNumber}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    @Operation(summary = "Subscribe to sanitized parcel updates over Server-Sent Events")
    public Flux<ServerSentEvent<PublicTrackingViewDto>> stream(@PathVariable String trackingNumber) {
        return enrichmentService.enrich(trackingNumber)
                .thenMany(Flux.defer(() -> {
                    Flux<ServerSentEvent<PublicTrackingViewDto>> views = broadcastHub.stream(trackingNumber)
                            .map(view -> ServerSentEvent.<PublicTrackingViewDto>builder().data(view).build());
                    Flux<ServerSentEvent<PublicTrackingViewDto>> heartbeats = Flux.interval(heartbeatInterval)
                            .map(sequence -> ServerSentEvent.<PublicTrackingViewDto>builder()
                                    .comment("heartbeat")
                                    .build());
                    return Flux.merge(views, heartbeats)
                            .takeUntil(event -> isTerminal(event.data()));
                }));
    }

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull WebSocketSession session) {
        String trackingNumber = resolveTrackingNumber(session);

        return enrichmentService.enrich(trackingNumber)
                .then(Mono.defer(() -> {
                    Flux<StreamItem> updates = broadcastHub.stream(trackingNumber)
                            .map(view -> new StreamItem(view, false));
                    Flux<StreamItem> heartbeats = Flux.interval(heartbeatInterval)
                            .map(sequence -> new StreamItem(null, true));

                    Flux<WebSocketMessage> messages = Flux.merge(updates, heartbeats)
                            .takeUntil(item -> isTerminal(item.view()))
                            .map(item -> item.heartbeat()
                                    ? session.pingMessage(factory -> factory.wrap(new byte[0]))
                                    : session.textMessage(toJson(item.view())));
                    return session.send(messages);
                }));
    }

    private String resolveTrackingNumber(WebSocketSession session) {
        String trackingNumber = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                .build()
                .getQueryParams()
                .getFirst("trackingNumber");
        if (trackingNumber == null) {
            trackingNumber = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri())
                    .build()
                    .getQueryParams()
                    .getFirst("trackingId");
        }
        return trackingNumber == null || trackingNumber.isBlank()
                ? "unknown"
                : trackingNumber;
    }

    private boolean isTerminal(PublicTrackingViewDto view) {
        return view != null
                && view.currentPublicStatus() != null
                && view.currentPublicStatus().isTerminal();
    }

    private String toJson(PublicTrackingViewDto view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tracking update", exception);
        }
    }

    private record StreamItem(PublicTrackingViewDto view, boolean heartbeat) {
    }
}
