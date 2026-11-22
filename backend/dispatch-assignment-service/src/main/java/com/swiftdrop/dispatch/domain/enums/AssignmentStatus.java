package com.swiftdrop.dispatch.domain.enums;

public enum AssignmentStatus {
    UNASSIGNED,
    ASSIGNED,
    REASSIGNED,
    /**
     * Between the two legs of a cross-district order: handed in at the pickup district's hub, on the way,
     * or not yet accepted by the drop-off district's hub. No courier holds it.
     */
    IN_TRANSFER,
    CANCELLED,
    COMPLETED;

    public boolean isTerminal() {
        return this == CANCELLED || this == COMPLETED;
    }
}
