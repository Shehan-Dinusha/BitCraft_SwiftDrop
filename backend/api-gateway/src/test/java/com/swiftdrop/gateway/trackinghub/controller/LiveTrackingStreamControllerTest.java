package com.swiftdrop.gateway.trackinghub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.gateway.trackinghub.service.TrackingBroadcastHubService;
import com.swiftdrop.gateway.trackinghub.service.TrackingDataSanitizerProxy;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveTrackingStreamControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TrackingBroadcastHubService hub =
            new TrackingBroadcastHubService(new TrackingDataSanitizerProxy(), objectMapper);
    private final LiveTrackingStreamController controller =
            new LiveTrackingStreamController(hub, objectMapper);

    @Test
    void emitsHeartbeatCommentsWhileWaitingForUpdates() {
        StepVerifier.withVirtualTime(() -> controller.stream("SD-WAIT-0000"))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(15))
                .expectNextMatches(event -> event.comment() != null && event.data() == null)
                .thenAwait(Duration.ofSeconds(15))
                .expectNextMatches(event -> event.comment() != null && event.data() == null)
                .thenCancel()
                .verify();
    }

    @Test
    void completesTheStreamAfterATerminalStatus() {
        StepVerifier.create(controller.stream("SD-DONE-0000"))
                .expectSubscription()
                .then(() -> hub.onStatusChanged(event("SD-DONE-0000", PublicOrderStatus.DELIVERED)))
                .assertNext(event -> {
                    assertThat(event.data()).isNotNull();
                    assertThat(event.data().currentPublicStatus()).isEqualTo(PublicOrderStatus.DELIVERED);
                })
                .verifyComplete();
    }

    @Test
    void keepsTheStreamOpenForNonTerminalStatuses() {
        StepVerifier.withVirtualTime(() -> controller.stream("SD-OPEN-0000"))
                .expectSubscription()
                .then(() -> hub.onStatusChanged(event("SD-OPEN-0000", PublicOrderStatus.IN_TRANSIT)))
                .assertNext(event -> assertThat(event.data().currentPublicStatus())
                        .isEqualTo(PublicOrderStatus.IN_TRANSIT))
                .thenAwait(Duration.ofSeconds(15))
                .expectNextMatches(event -> event.comment() != null && event.data() == null)
                .thenCancel()
                .verify();
    }

    private ParcelStatusChangedEvent event(String trackingNumber, PublicOrderStatus status) {
        return new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), trackingNumber, UUID.randomUUID(), UUID.randomUUID(),
                null, status, InternalQueueStatus.OUTBOUND,
                SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, null, Instant.now());
    }
}
