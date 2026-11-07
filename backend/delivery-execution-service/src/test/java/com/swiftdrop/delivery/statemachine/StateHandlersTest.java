package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.statemachine.states.AssignedState;
import com.swiftdrop.delivery.statemachine.states.CancelledState;
import com.swiftdrop.delivery.statemachine.states.CreatedState;
import com.swiftdrop.delivery.statemachine.states.DeliveredState;
import com.swiftdrop.delivery.statemachine.states.FailedState;
import com.swiftdrop.delivery.statemachine.states.InTransitState;
import com.swiftdrop.delivery.statemachine.states.OutForDeliveryState;
import com.swiftdrop.delivery.statemachine.states.PickedUpState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateHandlersTest {

    @Test
    @DisplayName("CreatedState allows only ASSIGNED and CANCELLED")
    void testCreatedState() {
        CreatedState state = new CreatedState();
        assertThat(state.state()).isEqualTo(DeliveryState.CREATED);
        assertThat(state.allowedNext()).containsExactlyInAnyOrder(
                DeliveryState.ASSIGNED,
                DeliveryState.CANCELLED
        );
    }

    @Test
    @DisplayName("AssignedState allows only PICKED_UP, FAILED, and CANCELLED")
    void testAssignedState() {
        AssignedState state = new AssignedState();
        assertThat(state.state()).isEqualTo(DeliveryState.ASSIGNED);
        assertThat(state.allowedNext()).containsExactlyInAnyOrder(
                DeliveryState.PICKED_UP,
                DeliveryState.FAILED,
                DeliveryState.CANCELLED
        );
    }

    @Test
    @DisplayName("PickedUpState allows only IN_TRANSIT and FAILED")
    void testPickedUpState() {
        PickedUpState state = new PickedUpState();
        assertThat(state.state()).isEqualTo(DeliveryState.PICKED_UP);
        assertThat(state.allowedNext()).containsExactlyInAnyOrder(
                DeliveryState.IN_TRANSIT,
                DeliveryState.FAILED
        );
    }

    @Test
    @DisplayName("InTransitState allows only OUT_FOR_DELIVERY and FAILED")
    void testInTransitState() {
        InTransitState state = new InTransitState();
        assertThat(state.state()).isEqualTo(DeliveryState.IN_TRANSIT);
        assertThat(state.allowedNext()).containsExactlyInAnyOrder(
                DeliveryState.OUT_FOR_DELIVERY,
                DeliveryState.FAILED
        );
    }

    @Test
    @DisplayName("OutForDeliveryState allows only DELIVERED and FAILED")
    void testOutForDeliveryState() {
        OutForDeliveryState state = new OutForDeliveryState();
        assertThat(state.state()).isEqualTo(DeliveryState.OUT_FOR_DELIVERY);
        assertThat(state.allowedNext()).containsExactlyInAnyOrder(
                DeliveryState.DELIVERED,
                DeliveryState.FAILED
        );
    }

    @Test
    @DisplayName("DeliveredState is a terminal state with no next transitions")
    void testDeliveredState() {
        DeliveredState state = new DeliveredState();
        assertThat(state.state()).isEqualTo(DeliveryState.DELIVERED);
        assertThat(state.allowedNext()).isEmpty();
    }

    @Test
    @DisplayName("FailedState is a terminal state with no next transitions")
    void testFailedState() {
        FailedState state = new FailedState();
        assertThat(state.state()).isEqualTo(DeliveryState.FAILED);
        assertThat(state.allowedNext()).isEmpty();
    }

    @Test
    @DisplayName("CancelledState is a terminal state with no next transitions")
    void testCancelledState() {
        CancelledState state = new CancelledState();
        assertThat(state.state()).isEqualTo(DeliveryState.CANCELLED);
        assertThat(state.allowedNext()).isEmpty();
    }
}

