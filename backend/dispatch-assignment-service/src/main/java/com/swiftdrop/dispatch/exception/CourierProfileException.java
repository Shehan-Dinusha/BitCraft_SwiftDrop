package com.swiftdrop.dispatch.exception;

import org.springframework.http.HttpStatus;

/** A courier vehicle profile request that cannot be completed, with the HTTP status to answer. */
public class CourierProfileException extends RuntimeException {

    private final HttpStatus status;

    public CourierProfileException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static CourierProfileException notFound() {
        return new CourierProfileException(HttpStatus.NOT_FOUND, "Courier vehicle profile not found");
    }
}
