package com.swiftdrop.gateway.trackinghub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingBroadcastHubServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TrackingBroadcastHubService hub =
            new TrackingBroadcastHubService(new TrackingDataSanitizerProxy(), objectMapper);

    @Test
    void reachesOnlySubscribersOfTheEventTrackingNumber() {
        ParcelStatusChangedEvent first = event(UUID.randomUUID(), "SD-AAA-1111",
                PublicOrderStatus.PICKED_UP, SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY);
        ParcelStatusChangedEvent second = event(UUID.randomUUID(), "SD-BBB-2222",
                PublicOrderStatus.IN_TRANSIT, SriLankanDistrict.GALLE, SriLankanDistrict.JAFFNA);

        StepVerifier.create(Flux.merge(hub.stream("SD-AAA-1111"), hub.stream("SD-BBB-2222")).take(2))
                .then(() -> {
                    hub.onStatusChanged(first);
                    hub.onStatusChanged(second);
                })
                .recordWith(ArrayList::new)
                .expectNextCount(2)
                .consumeRecordedWith(views -> assertThat(views)
                        .extracting(PublicTrackingViewDto::trackingNumber)
                        .containsExactlyInAnyOrder("SD-AAA-1111", "SD-BBB-2222"))
                .verifyComplete();
    }

    @Test
    void ignoresDuplicateEventIds() {
        UUID eventId = UUID.randomUUID();
        ParcelStatusChangedEvent event = event(eventId, "SD-DUP-3333",
                PublicOrderStatus.PICKED_UP, SriLankanDistrict.KANDY, SriLankanDistrict.MATALE);

        StepVerifier.create(hub.stream("SD-DUP-3333").take(1))
                .then(() -> {
                    hub.onStatusChanged(event);
                    hub.onStatusChanged(event);
                })
                .expectNextMatches(view -> view.statusTimeline().size() == 1)
                .verifyComplete();

        assertThat(hub.current("SD-DUP-3333").statusTimeline()).hasSize(1);
    }

    @Test
    void ignoresNullOrBlankTrackingNumbers() {
        hub.onStatusChanged(null);
        hub.onStatusChanged(event(UUID.randomUUID(), null,
                PublicOrderStatus.CREATED, SriLankanDistrict.COLOMBO, SriLankanDistrict.GALLE));
        hub.onStatusChanged(event(UUID.randomUUID(), "   ",
                PublicOrderStatus.CREATED, SriLankanDistrict.COLOMBO, SriLankanDistrict.GALLE));

        assertThat(hub.current(null)).isNull();
        assertThat(hub.current("")).isNull();
        assertThat(hub.current("   ")).isNull();
    }

    @Test
    void appendsTimelineAndKeepsTheFirstKnownDistricts() {
        Instant startedAt = Instant.parse("2026-10-26T06:00:00Z");
        ParcelStatusChangedEvent first = new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "SD-APP-4444", UUID.randomUUID(), UUID.randomUUID(),
                null, PublicOrderStatus.PICKED_UP, InternalQueueStatus.PICKED_UP,
                SriLankanDistrict.COLOMBO, SriLankanDistrict.KANDY, null, startedAt);
        ParcelStatusChangedEvent second = new ParcelStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "SD-APP-4444", first.orderId(), first.customerId(),
                PublicOrderStatus.PICKED_UP, PublicOrderStatus.IN_TRANSIT, InternalQueueStatus.OUTBOUND,
                null, SriLankanDistrict.MATALE, null, startedAt.plusSeconds(3600));

        StepVerifier.create(hub.stream("SD-APP-4444").take(2))
                .then(() -> {
                    hub.onStatusChanged(first);
                    hub.onStatusChanged(second);
                })
                .expectNextMatches(view -> view.currentPublicStatus() == PublicOrderStatus.PICKED_UP)
                .expectNextMatches(view -> view.currentPublicStatus() == PublicOrderStatus.IN_TRANSIT)
                .verifyComplete();

        PublicTrackingViewDto view = hub.current("SD-APP-4444");
        assertThat(view.statusTimeline()).hasSize(2);
        assertThat(view.originDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(view.destinationDistrict()).isEqualTo(SriLankanDistrict.KANDY);
    }

    private ParcelStatusChangedEvent event(UUID eventId,
                                           String trackingNumber,
                                           PublicOrderStatus status,
                                           SriLankanDistrict district,
                                           SriLankanDistrict destinationDistrict) {
        return new ParcelStatusChangedEvent(
                eventId, UUID.randomUUID(), trackingNumber, UUID.randomUUID(), UUID.randomUUID(),
                null, status, null, district, destinationDistrict, null, Instant.now());
    }
}
