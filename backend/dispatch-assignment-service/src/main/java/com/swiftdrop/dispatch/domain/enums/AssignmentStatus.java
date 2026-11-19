package com.swiftdrop.dispatch.domain.enums;

public enum AssignmentStatus {
    UNASSIGNED,
    ASSIGNED,
    REASSIGNED,
    CANCELLED,
    COMPLETED;

    public boolean isTerminal() {
        return this == CANCELLED || this == COMPLETED;
    }
}
