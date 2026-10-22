package com.swiftdrop.delivery.exception;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(String message) { super(message); }
}
