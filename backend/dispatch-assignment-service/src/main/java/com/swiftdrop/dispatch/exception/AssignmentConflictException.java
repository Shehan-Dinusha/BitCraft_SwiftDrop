package com.swiftdrop.dispatch.exception;

/**
 * The requested assignment operation conflicts with the order's current
 * assignment, for example assigning an order that already has a courier or
 * reassigning from a courier that is no longer the assigned one.
 */
public class AssignmentConflictException extends RuntimeException {
    public AssignmentConflictException(String message) {
        super(message);
    }
}
