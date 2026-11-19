package com.swiftdrop.dispatch.domain.state;

import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;

public final class AssignmentStateMachine {

    private AssignmentStateMachine() {
    }

    /**
     * Applies a lifecycle transition and returns whether the status changed.
     * Repeating the same terminal event is intentionally idempotent.
     */
    public static boolean transition(AssignmentStatus current, AssignmentStatus target) {
        if (current == null) {
            throw new InvalidAssignmentTransitionException("Assignment has no current lifecycle state");
        }
        if (target == null) {
            throw new InvalidAssignmentTransitionException("Assignment transition target cannot be null");
        }
        if (current == target) {
            return false;
        }
        if (!isAllowed(current, target)) {
            throw new InvalidAssignmentTransitionException(
                    "Invalid assignment transition: " + current + " -> " + target);
        }
        return true;
    }

    private static boolean isAllowed(AssignmentStatus current, AssignmentStatus target) {
        return switch (current) {
            case UNASSIGNED -> target == AssignmentStatus.ASSIGNED
                    || target == AssignmentStatus.CANCELLED;
            case ASSIGNED -> target == AssignmentStatus.REASSIGNED
                    || target == AssignmentStatus.COMPLETED
                    || target == AssignmentStatus.CANCELLED;
            case REASSIGNED -> target == AssignmentStatus.COMPLETED
                    || target == AssignmentStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };
    }
}
