package com.swiftdrop.webhook.exception;

/**
 * Rejected endpoint subscription patterns. Extends {@link IllegalArgumentException} so the existing
 * handler maps it to 400; the message names only the caller's own tokens.
 */
public class InvalidEventSubscriptionException extends IllegalArgumentException {

    public InvalidEventSubscriptionException(String message) {
        super(message);
    }
}
