package com.swiftdrop.delivery.statemachine;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
        assertThat(stateMachine.canTransition(DeliveryState.PICKED_UP, DeliveryState.FAILED)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.IN_TRANSIT, DeliveryState.FAILED)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.OUT_FOR_DELIVERY, DeliveryState.FAILED)).isTrue();

        assertThat(stateMachine.transition(DeliveryState.ASSIGNED, DeliveryState.FAILED)).isEqualTo(DeliveryState.FAILED);
        assertThat(stateMachine.transition(DeliveryState.PICKED_UP, DeliveryState.FAILED)).isEqualTo(DeliveryState.FAILED);
        assertThat(stateMachine.transition(DeliveryState.IN_TRANSIT, DeliveryState.FAILED)).isEqualTo(DeliveryState.FAILED);
        assertThat(stateMachine.transition(DeliveryState.OUT_FOR_DELIVERY, DeliveryState.FAILED)).isEqualTo(DeliveryState.FAILED);
    }

    @Test
    @DisplayName("Valid cancellation from CREATED and ASSIGNED")
    void testCancellationTransitions() {
        assertThat(stateMachine.canTransition(DeliveryState.CREATED, DeliveryState.CANCELLED)).isTrue();
        assertThat(stateMachine.canTransition(DeliveryState.ASSIGNED, DeliveryState.CANCELLED)).isTrue();

        assertThat(stateMachine.transition(DeliveryState.CREATED, DeliveryState.CANCELLED)).isEqualTo(DeliveryState.CANCELLED);
        assertThat(stateMachine.transition(DeliveryState.ASSIGNED, DeliveryState.CANCELLED)).isEqualTo(DeliveryState.CANCELLED);
    }

    @Test
    @DisplayName("Illegal forward skips throw InvalidStateTransitionException")
    void testIllegalForwardSkips() {
        // Cannot jump directly from CREATED to DELIVERED, IN_TRANSIT, OUT_FOR_DELIVERY, or FAILED
        assertIllegalTransition(DeliveryState.CREATED, DeliveryState.DELIVERED);
        assertIllegalTransition(DeliveryState.CREATED, DeliveryState.IN_TRANSIT);
        assertIllegalTransition(DeliveryState.CREATED, DeliveryState.OUT_FOR_DELIVERY);
        assertIllegalTransition(DeliveryState.CREATED, DeliveryState.FAILED);

        // Cannot jump directly from ASSIGNED to DELIVERED or OUT_FOR_DELIVERY
        assertIllegalTransition(DeliveryState.ASSIGNED, DeliveryState.DELIVERED);
        assertIllegalTransition(DeliveryState.ASSIGNED, DeliveryState.OUT_FOR_DELIVERY);

        // Cannot jump directly from IN_TRANSIT to DELIVERED (must go through OUT_FOR_DELIVERY)
        assertIllegalTransition(DeliveryState.IN_TRANSIT, DeliveryState.DELIVERED);
    }

    @ParameterizedTest
    @EnumSource(DeliveryState.class)
    @DisplayName("Terminal state DELIVERED cannot transition to any other state")
    void testDeliveredCannotTransition(DeliveryState targetState) {
        assertIllegalTransition(DeliveryState.DELIVERED, targetState);
    }

    @ParameterizedTest
    @EnumSource(DeliveryState.class)
    @DisplayName("Terminal state FAILED cannot transition to any other state")
    void testFailedCannotTransition(DeliveryState targetState) {
        assertIllegalTransition(DeliveryState.FAILED, targetState);
    }

    @ParameterizedTest
    @EnumSource(DeliveryState.class)
    @DisplayName("Terminal state CANCELLED cannot transition to any other state")
    void testCancelledCannotTransition(DeliveryState targetState) {
        assertIllegalTransition(DeliveryState.CANCELLED, targetState);
    }

    @Test
    @DisplayName("Null arguments return false and do not throw unhandled exceptions")
    void testNullSafety() {
        assertThat(stateMachine.canTransition(null, DeliveryState.ASSIGNED)).isFalse();
        assertThat(stateMachine.canTransition(DeliveryState.CREATED, null)).isFalse();
        assertThat(stateMachine.canTransition(null, null)).isFalse();
    }

    private void assertIllegalTransition(DeliveryState from, DeliveryState to) {
        assertThat(stateMachine.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> stateMachine.transition(from, to))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Invalid delivery transition: " + from + " -> " + to);
    }
}
