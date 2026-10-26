package com.swiftdrop.gateway.trackinghub.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping({"/api/v1/public/track", "/api/v1/tracking"})
@Tag(name = "Real-Time Tracking Hub", description = "SSE and WebSocket parcel update streams")
public class LiveTrackingStreamController implements WebSocketHandler {
    private final TrackingBroadcastHubService broadcastHub;
    private final ObjectMapper objectMapper;

    public LiveTrackingStreamController(TrackingBroadcastHubService broadcastHub, ObjectMapper objectMapper) {
        this.broadcastHub = broadcastHub;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/{trackingNumber}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    @Operation(summary = "Subscribe to sanitized parcel updates over Server-Sent Events")
    public Flux<PublicTrackingViewDto> stream(@PathVariable String trackingNumber) {
        return broadcastHub.stream(trackingNumber);
    }

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull WebSocketSession session) {
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
        String resolvedTrackingNumber = trackingNumber == null || trackingNumber.isBlank()
                ? "unknown"
                : trackingNumber;
        Flux<WebSocketMessage> messages = broadcastHub.stream(resolvedTrackingNumber)
                .map(this::toJson)
                .map(session::textMessage);
        return session.send(messages);
    }

    private String toJson(PublicTrackingViewDto view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tracking update", exception);
        }
    }
}
