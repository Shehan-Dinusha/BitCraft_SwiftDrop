package com.swiftdrop.contracts.security;

/** Required verified identity is missing or a supplied identity header is malformed. */
public final class UnauthenticatedCallerException extends CallerContextException {
    public UnauthenticatedCallerException(String message) {
        super(401, message);
    }
}
