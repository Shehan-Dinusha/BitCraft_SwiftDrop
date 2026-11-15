package com.swiftdrop.dispatch.exception;

/**
 * A courier shift cannot be ended while the courier still has active parcels.
 */
public class ShiftEndNotAllowedException extends RuntimeException {
    public ShiftEndNotAllowedException(String message) {
        super(message);
    }
}
