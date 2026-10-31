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
}
