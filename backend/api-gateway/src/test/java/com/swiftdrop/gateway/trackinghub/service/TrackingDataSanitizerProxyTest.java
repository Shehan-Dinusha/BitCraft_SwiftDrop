package com.swiftdrop.gateway.trackinghub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingDataSanitizerProxyTest {

    private final TrackingDataSanitizerProxy sanitizer = new TrackingDataSanitizerProxy();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void buildsPiiFreeViewFromStatusEvent() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-10-26T06:30:00Z");

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SD-2026-000123",
                orderId,
                customerId,
                PublicOrderStatus.PICKED_UP,
                PublicOrderStatus.OUT_FOR_DELIVERY,
                InternalQueueStatus.OUT_FOR_DELIVERY,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.KANDY,
                null,
                timestamp
        );

        PublicTrackingViewDto view = sanitizer.fromStatus(event);
        String json = objectMapper.writeValueAsString(view);

        assertThat(view.trackingNumber()).isEqualTo("SD-2026-000123");
        assertThat(view.currentPublicStatus()).isEqualTo(PublicOrderStatus.OUT_FOR_DELIVERY);
        assertThat(view.originDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(view.destinationDistrict()).isEqualTo(SriLankanDistrict.KANDY);
        assertThat(view.statusTimeline()).hasSize(1);
        assertThat(view.statusTimeline().getFirst().timestamp()).isEqualTo(timestamp);
        assertThat(json)
                .doesNotContain(customerId.toString())
                .doesNotContain(orderId.toString())
                .doesNotContain("courierId")
                .doesNotContain("address");
    }

    @Test
    void derivesPublicStatusFromInternalStatusWhenPublicStatusIsMissing() {
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                null, null, "SD-1", null, null, null, null,
                InternalQueueStatus.OUTBOUND, SriLankanDistrict.GALLE,
                SriLankanDistrict.JAFFNA, null, null
        );

        PublicTrackingViewDto view = sanitizer.fromStatus(event);

        assertThat(view.currentPublicStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
    }

    @Test
    void mapsAllInternalStatusesToPublicStatuses() {
        assertThat(InternalQueueStatus.CREATED.toPublicStatus()).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(InternalQueueStatus.UNASSIGNED.toPublicStatus()).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(InternalQueueStatus.PICKUP_ASSIGNED.toPublicStatus()).isEqualTo(PublicOrderStatus.PICKUP_ASSIGNED);
        assertThat(InternalQueueStatus.PICKED_UP.toPublicStatus()).isEqualTo(PublicOrderStatus.PICKED_UP);
        assertThat(InternalQueueStatus.AT_HUB.toPublicStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        assertThat(InternalQueueStatus.OUTBOUND.toPublicStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        assertThat(InternalQueueStatus.INCOMING.toPublicStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        assertThat(InternalQueueStatus.OUT_FOR_DELIVERY.toPublicStatus()).isEqualTo(PublicOrderStatus.OUT_FOR_DELIVERY);
        assertThat(InternalQueueStatus.DELIVERED.toPublicStatus()).isEqualTo(PublicOrderStatus.DELIVERED);
        assertThat(InternalQueueStatus.FAILED_RETURN.toPublicStatus()).isEqualTo(PublicOrderStatus.RETURNED_TO_SENDER);
        assertThat(InternalQueueStatus.CANCELLED.toPublicStatus()).isEqualTo(PublicOrderStatus.CANCELLED);
    }

    @Test
    void mapsLegacyDeliveryStates() {
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.CREATED)).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.ASSIGNED)).isEqualTo(PublicOrderStatus.PICKUP_ASSIGNED);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.PICKED_UP)).isEqualTo(PublicOrderStatus.PICKED_UP);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.IN_TRANSIT)).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.DELIVERED)).isEqualTo(PublicOrderStatus.DELIVERED);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.FAILED)).isEqualTo(PublicOrderStatus.RETURNED_TO_SENDER);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.CANCELLED)).isEqualTo(PublicOrderStatus.CANCELLED);
        assertThat(PublicOrderStatus.fromDeliveryState(null)).isNull();
    }

    @Test
    void appendsLegacyMilestoneWithoutDroppingEarlierHistory() {
        DeliveryStateChangedEvent first = new DeliveryStateChangedEvent(
                null, null, UUID.randomUUID(), UUID.randomUUID(), null,
                null, DeliveryState.CREATED, null, Instant.parse("2026-10-26T06:00:00Z")
        );
        DeliveryStateChangedEvent second = new DeliveryStateChangedEvent(
                null, null, first.deliveryId(), first.orderId(), null,
                DeliveryState.CREATED, DeliveryState.PICKED_UP, null, Instant.parse("2026-10-26T07:00:00Z")
        );

        PublicTrackingViewDto view = sanitizer.appendLegacy(sanitizer.fromLegacy(first), second);

        assertThat(view.currentPublicStatus()).isEqualTo(PublicOrderStatus.PICKED_UP);
        assertThat(view.statusTimeline()).hasSize(2);
    }
}
