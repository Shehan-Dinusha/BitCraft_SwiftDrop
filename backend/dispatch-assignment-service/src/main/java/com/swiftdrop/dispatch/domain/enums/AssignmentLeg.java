package com.swiftdrop.dispatch.domain.enums;

/**
 * Which part of the journey an assignment is on. Same-district orders only have a FIRST_MILE (pickup to
 * recipient). A cross-district order moves to LAST_MILE when the drop-off district's hub accepts it.
 */
public enum AssignmentLeg {
    /** Pickup by a courier of the pickup district; its dispatcher manages it. */
    FIRST_MILE,
    /** From the drop-off district's hub to the recipient; that district's dispatcher manages it. */
    LAST_MILE
}
