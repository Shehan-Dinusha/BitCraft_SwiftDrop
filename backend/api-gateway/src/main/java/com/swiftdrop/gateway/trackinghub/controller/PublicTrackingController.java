package com.swiftdrop.gateway.trackinghub.controller;

import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import com.swiftdrop.gateway.trackinghub.service.TrackingViewEnrichmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping({"/api/v1/public/track", "/api/v1/tracking"})
@Tag(name = "Real-Time Tracking Hub", description = "Public sanitized parcel tracking APIs")
@RequiredArgsConstructor
public class PublicTrackingController {

    private final TrackingViewEnrichmentService enrichmentService;

    @GetMapping("/{trackingNumber}")
    @Operation(summary = "Get the latest sanitized parcel tracking view")
    public Mono<PublicTrackingViewDto> getTracking(@PathVariable String trackingNumber) {
        return enrichmentService.enrich(trackingNumber)
                .flatMap(optional -> {
                    if (optional.isEmpty()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Tracking data is not available"));
                    }
                    return Mono.just(optional.get());
                });
    }
}
