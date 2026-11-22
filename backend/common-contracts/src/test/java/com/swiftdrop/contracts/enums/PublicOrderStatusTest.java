package com.swiftdrop.contracts.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicOrderStatusTest {
    @Test
    void mapsOutForDeliveryAndFailureToTheirPublicStatuses() {
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.OUT_FOR_DELIVERY))
                .isEqualTo(PublicOrderStatus.OUT_FOR_DELIVERY);
        assertThat(PublicOrderStatus.fromDeliveryState(DeliveryState.FAILED))
                .isEqualTo(PublicOrderStatus.FAILED);
        assertThat(PublicOrderStatus.FAILED.isTerminal()).isTrue();
        assertThat(InternalQueueStatus.fromDeliveryState(DeliveryState.IN_TRANSIT))
                .isEqualTo(InternalQueueStatus.OUTBOUND);
        assertThat(InternalQueueStatus.fromDeliveryState(DeliveryState.OUT_FOR_DELIVERY))
                .isEqualTo(InternalQueueStatus.OUT_FOR_DELIVERY);
        assertThat(InternalQueueStatus.fromDeliveryState(DeliveryState.FAILED))
                .isEqualTo(InternalQueueStatus.FAILED_ATTEMPT);
    }

    @Test
    void hubStepsStayInTransitForCustomersButDifferInternally() {
        for (DeliveryState hubStep : new DeliveryState[]{
                DeliveryState.AT_ORIGIN_HUB, DeliveryState.IN_TRANSIT, DeliveryState.AT_DESTINATION_HUB}) {
            assertThat(PublicOrderStatus.fromDeliveryState(hubStep)).isEqualTo(PublicOrderStatus.IN_TRANSIT);
        }
        assertThat(InternalQueueStatus.fromDeliveryState(DeliveryState.AT_ORIGIN_HUB))
                .isEqualTo(InternalQueueStatus.AT_HUB);
        assertThat(InternalQueueStatus.fromDeliveryState(DeliveryState.AT_DESTINATION_HUB))
                .isEqualTo(InternalQueueStatus.INCOMING);
        assertThat(InternalQueueStatus.INCOMING.toPublicStatus()).isEqualTo(PublicOrderStatus.IN_TRANSIT);
    }
}
