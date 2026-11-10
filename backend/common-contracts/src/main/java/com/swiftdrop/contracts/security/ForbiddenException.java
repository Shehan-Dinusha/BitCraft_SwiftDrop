package com.swiftdrop.contracts.security;

/** A valid caller does not satisfy the operation's explicit authorization rule. */
public final class ForbiddenException extends CallerContextException {
    public ForbiddenException(String message) {
        super(403, message);
    }
}
