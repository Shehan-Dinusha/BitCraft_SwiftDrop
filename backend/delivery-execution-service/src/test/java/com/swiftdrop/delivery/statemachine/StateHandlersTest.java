package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import com.swiftdrop.delivery.statemachine.guards.ProofOfDeliveryGuard;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void testOutForDeliveryStateAllowedTransitions() {
        OutForDeliveryState state = new OutForDeliveryState();
        assertThat(state.state()).isEqualTo(DeliveryState.OUT_FOR_DELIVERY);
        assertThat(state.allowedNext()).containsExactlyInAnyOrder(
                DeliveryState.DELIVERED,
                DeliveryState.FAILED
        );
    }

    @Test
    @DisplayName("OutForDeliveryState validate: DELIVERED requires proof of delivery")
    void testOutForDeliveryStateRequiresProofForDelivered() {
        ProofOfDeliveryGuard mockGuard = mock(ProofOfDeliveryGuard.class);
        OutForDeliveryState state = new OutForDeliveryState(mockGuard);

        UUID deliveryId = UUID.randomUUID();
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);

        TransitionDeliveryRequest request = new TransitionDeliveryRequest(
                DeliveryState.DELIVERED,
                "Delivered successfully",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                null,
                "COURIER"
        );

        when(mockGuard.isSatisfied(deliveryId)).thenReturn(false);
        assertThatThrownBy(() -> state.validate(request, session))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Proof of delivery must be verified");

        when(mockGuard.isSatisfied(deliveryId)).thenReturn(true);
        assertThatCode(() -> state.validate(request, session))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OutForDeliveryState validate: FAILED requires a DeliveryFailureReason")
    void testOutForDeliveryStateRequiresReasonForFailed() {
        OutForDeliveryState state = new OutForDeliveryState();
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());

        TransitionDeliveryRequest requestWithoutReason = new TransitionDeliveryRequest(
                DeliveryState.FAILED,
                "Recipient not home",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                null,
                "COURIER"
        );

        assertThatThrownBy(() -> state.validate(requestWithoutReason, session))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Delivery failure requires a valid DeliveryFailureReason");

        TransitionDeliveryRequest requestWithReason = new TransitionDeliveryRequest(
                DeliveryState.FAILED,
                "Recipient not home",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                DeliveryFailureReason.CUSTOMER_UNAVAILABLE,
                "COURIER"
        );

        assertThatCode(() -> state.validate(requestWithReason, session))
                .doesNotThrowAnyException();
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
