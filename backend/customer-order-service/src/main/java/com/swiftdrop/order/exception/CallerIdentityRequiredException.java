package com.swiftdrop.order.exception;

/**
 * Thrown when an operation needs to know who the caller is (the {@code X-User-Id} header set by the
 * gateway) and no usable identity was supplied. Mapped to 401.
 */
public class CallerIdentityRequiredException extends RuntimeException {
    public CallerIdentityRequiredException(String message) {
        super(message);
    }
}
