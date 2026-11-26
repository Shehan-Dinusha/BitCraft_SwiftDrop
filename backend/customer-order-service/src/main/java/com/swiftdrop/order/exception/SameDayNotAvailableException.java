package com.swiftdrop.order.exception;

/** Same day delivery was asked for between districts, after 11:00, or on a closed day. */
public class SameDayNotAvailableException extends RuntimeException {
    public SameDayNotAvailableException(String message) {
        super(message);
    }
}
