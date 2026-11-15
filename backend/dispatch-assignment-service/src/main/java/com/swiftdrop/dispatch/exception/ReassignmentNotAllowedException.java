package com.swiftdrop.dispatch.exception;

/**
 * Normal reassignment was requested for an order that dispatch has recorded as
 * picked up. Exceptional post-pickup transfers are a separate workflow.
 */
public class ReassignmentNotAllowedException extends RuntimeException {
    public ReassignmentNotAllowedException(String message) {
        super(message);
    }
}
