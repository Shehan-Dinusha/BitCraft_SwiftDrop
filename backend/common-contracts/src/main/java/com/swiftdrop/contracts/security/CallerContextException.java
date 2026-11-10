package com.swiftdrop.contracts.security;

/** Framework-neutral failure that consuming services map to their API error envelope. */
public abstract class CallerContextException extends RuntimeException {
    private final int statusCode;

    protected CallerContextException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
