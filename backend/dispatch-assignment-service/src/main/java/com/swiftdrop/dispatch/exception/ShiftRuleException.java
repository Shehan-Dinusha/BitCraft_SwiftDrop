package com.swiftdrop.dispatch.exception;

/** A shift or break asked for outside the working-day rules (closed day, outside hours, second break). */
public class ShiftRuleException extends RuntimeException {
    public ShiftRuleException(String message) {
        super(message);
    }
}
