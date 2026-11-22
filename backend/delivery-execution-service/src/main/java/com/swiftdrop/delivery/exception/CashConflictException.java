package com.swiftdrop.delivery.exception;

/** The cash step does not fit the current state: a wrong amount, nothing to hand over, already counted. */
public class CashConflictException extends RuntimeException {

    public CashConflictException(String message) {
        super(message);
    }
}
