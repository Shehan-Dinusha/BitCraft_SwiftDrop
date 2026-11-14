package com.swiftdrop.notification.service.email;

/** Raised when an email provider does not accept a message. Messages are safe to store in dispatch logs. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
