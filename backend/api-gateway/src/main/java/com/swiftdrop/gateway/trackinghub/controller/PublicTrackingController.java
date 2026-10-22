package com.swiftdrop.gateway.trackinghub.controller;

import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/tracking")
@Tag(name = "Real-Time Tracking Hub", description = "Public sanitized parcel tracking APIs")
public class PublicTrackingController {
    private final TrackingBroadcastHubService broadcastHub;

    public PublicTrackingController(TrackingBroadcastHubService broadcastHub) {
        this.broadcastHub = broadcastHub;
    }

    @GetMapping("/{trackingId}")
    @Operation(summary = "Get the latest sanitized parcel tracking view")
    public Mono<PublicTrackingViewDto> getTracking(@PathVariable String trackingId) {
        PublicTrackingViewDto view = broadcastHub.current(trackingId);
        if (view == null) {
            return Mono.error(new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Tracking data is not available"));
        }
        return Mono.just(view);
    }
}
