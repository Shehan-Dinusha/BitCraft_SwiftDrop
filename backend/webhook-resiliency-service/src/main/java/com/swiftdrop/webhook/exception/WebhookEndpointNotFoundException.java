package com.swiftdrop.webhook.exception;

public class WebhookEndpointNotFoundException extends RuntimeException {
    public WebhookEndpointNotFoundException(String message) {
        super(message);
    }
}
