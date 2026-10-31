package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryStateMachineTest {

    private DeliveryStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new DeliveryStateMachine();
    }

    @Test
    @DisplayName("Valid happy path lifecycle: CREATED -> ASSIGNED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED")
    void testHappyPathTransitions() {
        assertThat(stateMachine.canTransition(DeliveryState.CREATED, DeliveryState.ASSIGNED)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.ASSIGNED, DeliveryState.PICKED_UP)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.PICKED_UP, DeliveryState.IN_TRANSIT)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.IN_TRANSIT, DeliveryState.OUT_FOR_DELIVERY)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.OUT_FOR_DELIVERY, DeliveryState.DELIVERED)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.IN_TRANSIT, DeliveryState.DELIVERED)).isFalse();

        assertThat(stateMachine.transition(DeliveryState.CREATED, DeliveryState.ASSIGNED)).isEqualTo(DeliveryState.ASSIGNED);
        assertThat(stateMachine.transition(DeliveryState.ASSIGNED, DeliveryState.PICKED_UP)).isEqualTo(DeliveryState.PICKED_UP);
        assertThat(stateMachine.transition(DeliveryState.PICKED_UP, DeliveryState.IN_TRANSIT)).isEqualTo(DeliveryState.IN_TRANSIT);
        assertThat(stateMachine.transition(DeliveryState.IN_TRANSIT, DeliveryState.OUT_FOR_DELIVERY)).isEqualTo(DeliveryState.OUT_FOR_DELIVERY);
        assertThat(stateMachine.transition(DeliveryState.OUT_FOR_DELIVERY, DeliveryState.DELIVERED)).isEqualTo(DeliveryState.DELIVERED);
    }

    @Test
    @DisplayName("Valid failure transitions: ASSIGNED -> FAILED, IN_TRANSIT -> FAILED, OUT_FOR_DELIVERY -> FAILED")
    void testFailureTransitions() {
        assertThat(stateMachine.canTransition(DeliveryState.ASSIGNED, DeliveryState.FAILED)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.IN_TRANSIT, DeliveryState.FAILED)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.OUT_FOR_DELIVERY, DeliveryState.FAILED)).isTrue();
    }

    @Test
    @DisplayName("Valid cancellation from CREATED and ASSIGNED")
    void testCancellationTransitions() {
        assertThat(stateMachine.canTransition(DeliveryState.CREATED, DeliveryState.CANCELLED)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.ASSIGNED, DeliveryState.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("Invalid transitions throw InvalidStateTransitionException")
    void testInvalidTransitions() {
        assertThat(stateMachine.canTransition(DeliveryState.CREATED, DeliveryState.DELIVERED)).isFalse();
        assertThat(stateMachine.canTransition(DeliveryState.DELIVERED, DeliveryState.IN_TRANSIT)).isFalse();
        assertThat(stateMachine.canTransition(DeliveryState.FAILED, DeliveryState.ASSIGNED)).isFalse();

        assertThatThrownBy(() -> stateMachine.transition(DeliveryState.CREATED, DeliveryState.DELIVERED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Invalid delivery transition");
    }
}
