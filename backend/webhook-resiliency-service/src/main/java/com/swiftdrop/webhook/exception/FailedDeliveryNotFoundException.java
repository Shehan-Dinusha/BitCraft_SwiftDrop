package com.swiftdrop.webhook.exception;

public class FailedDeliveryNotFoundException extends RuntimeException {
    public FailedDeliveryNotFoundException(String message) {
        super(message);
    }
}
