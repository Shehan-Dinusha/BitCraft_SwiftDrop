package com.swiftdrop.gateway.trackinghub.controller;

import com.swiftdrop.contracts.dto.tracking.HistoryEntry;
import com.swiftdrop.contracts.dto.tracking.InternalTrackingView;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.gateway.trackinghub.client.OrderTrackingClient;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import com.swiftdrop.gateway.trackinghub.service.TrackingDataSanitizerProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicTrackingControllerTest {

    private TrackingBroadcastHubService broadcastHub;
    private OrderTrackingClient orderClient;
    private TrackingDataSanitizerProxy sanitizer;
    private PublicTrackingController controller;

    @BeforeEach
    void setUp() {
        broadcastHub = mock(TrackingBroadcastHubService.class);
        orderClient = mock(OrderTrackingClient.class);
        sanitizer = new TrackingDataSanitizerProxy();
        controller = new PublicTrackingController(broadcastHub, orderClient, sanitizer);
    }

    @Test
    void cacheHitReturnsHubView() {
        PublicTrackingViewDto hubView = new PublicTrackingViewDto(
                "SD-HUB-001",
                PublicOrderStatus.PICKED_UP,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                List.of()
        );
        when(broadcastHub.current("SD-HUB-001")).thenReturn(hubView);

        StepVerifier.create(controller.getTracking("SD-HUB-001"))
                .expectNextMatches(v -> v.trackingNumber().equals("SD-HUB-001"))
                .verifyComplete();

        Mockito.verify(orderClient, Mockito.never()).getTracking(anyString());
    }

    @Test
    void cacheMissWithOrderServiceSuccessReturnsSanitizedView() {
        when(broadcastHub.current("SD-MISS-001")).thenReturn(null);

        InternalTrackingView internal = new InternalTrackingView(
                "SD-MISS-001",
                PublicOrderStatus.IN_TRANSIT,
                List.of(new HistoryEntry(PublicOrderStatus.CREATED, Instant.parse("2026-10-26T06:00:00Z"))),
                "Colombo",
                SriLankanDistrict.COLOMBO,
                "Kandy",
                SriLankanDistrict.KANDY
        );

        when(orderClient.getTracking("SD-MISS-001")).thenReturn(Mono.just(Optional.of(internal)));

        StepVerifier.create(controller.getTracking("SD-MISS-001"))
                .expectNextMatches(v -> v.trackingNumber().equals("SD-MISS-001")
                        && v.currentPublicStatus() == PublicOrderStatus.IN_TRANSIT)
                .verifyComplete();

        Mockito.verify(broadcastHub).updateCache(eq("SD-MISS-001"), Mockito.any(PublicTrackingViewDto.class));
    }

    @Test
    void cacheMissWithOrderService404ReturnsNotFound() {
        when(broadcastHub.current("SD-404-001")).thenReturn(null);
        when(orderClient.getTracking("SD-404-001")).thenReturn(Mono.just(Optional.empty()));

        StepVerifier.create(controller.getTracking("SD-404-001"))
                .expectErrorMatches(err -> err instanceof ResponseStatusException
                        && ((ResponseStatusException) err).getStatusCode() == HttpStatus.NOT_FOUND)
                .verify();
    }

    @Test
    void cacheMissWithOrderServiceErrorReturnsNotFound() {
        when(broadcastHub.current("SD-ERROR-001")).thenReturn(null);
        when(orderClient.getTracking("SD-ERROR-001")).thenReturn(Mono.just(Optional.empty()));

        StepVerifier.create(controller.getTracking("SD-ERROR-001"))
                .expectErrorMatches(err -> err instanceof ResponseStatusException
                        && ((ResponseStatusException) err).getStatusCode() == HttpStatus.NOT_FOUND)
                .verify();
    }

    @Test
    void cacheMissWithOrderServiceTimeoutReturnsNotFound() {
        when(broadcastHub.current("SD-TIMEOUT-001")).thenReturn(null);
        when(orderClient.getTracking("SD-TIMEOUT-001")).thenReturn(Mono.just(Optional.empty()));

        StepVerifier.create(controller.getTracking("SD-TIMEOUT-001"))
                .expectErrorMatches(err -> err instanceof ResponseStatusException
                        && ((ResponseStatusException) err).getStatusCode() == HttpStatus.NOT_FOUND)
                .verify();
    }
}