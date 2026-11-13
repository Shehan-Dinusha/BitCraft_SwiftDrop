package com.swiftdrop.dispatch.domain.state;

import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentStateMachineTest {

    @Test
    void permitsSupportedAssignmentLifecycleTransitions() {
        assertThat(AssignmentStateMachine.transition(
                AssignmentStatus.UNASSIGNED, AssignmentStatus.ASSIGNED)).isTrue();
        assertThat(AssignmentStateMachine.transition(
                AssignmentStatus.UNASSIGNED, AssignmentStatus.CANCELLED)).isTrue();
        assertThat(AssignmentStateMachine.transition(
                AssignmentStatus.ASSIGNED, AssignmentStatus.REASSIGNED)).isTrue();
        assertThat(AssignmentStateMachine.transition(
                AssignmentStatus.ASSIGNED, AssignmentStatus.COMPLETED)).isTrue();
        assertThat(AssignmentStateMachine.transition(
                AssignmentStatus.ASSIGNED, AssignmentStatus.CANCELLED)).isTrue();
        assertThat(AssignmentStateMachine.transition(
                AssignmentStatus.REASSIGNED, AssignmentStatus.COMPLETED)).isTrue();
        assertThat(AssignmentStateMachine.transition(
                AssignmentStatus.REASSIGNED, AssignmentStatus.CANCELLED)).isTrue();
    }

    @Test
    void rejectsTransitionsOutOfTerminalStates() {
        assertThatThrownBy(() -> AssignmentStateMachine.transition(
                AssignmentStatus.COMPLETED, AssignmentStatus.ASSIGNED))
                .isInstanceOf(InvalidAssignmentTransitionException.class);
        assertThatThrownBy(() -> AssignmentStateMachine.transition(
                AssignmentStatus.CANCELLED, AssignmentStatus.REASSIGNED))
                .isInstanceOf(InvalidAssignmentTransitionException.class);
        assertThatThrownBy(() -> AssignmentStateMachine.transition(
                AssignmentStatus.UNASSIGNED, AssignmentStatus.COMPLETED))
                .isInstanceOf(InvalidAssignmentTransitionException.class);
    }

    @Test
    void repeatedSameStateTransitionIsIdempotent() {
        assertThat(AssignmentStateMachine.transition(
                AssignmentStatus.CANCELLED, AssignmentStatus.CANCELLED)).isFalse();
    }
}
