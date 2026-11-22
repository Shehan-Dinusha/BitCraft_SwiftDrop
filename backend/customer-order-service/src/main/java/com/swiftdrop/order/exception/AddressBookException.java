package com.swiftdrop.order.exception;

import org.springframework.http.HttpStatus;

/** An address-book request that cannot be done: an unknown address (404) or a full book (409). */
public class AddressBookException extends RuntimeException {

    private final HttpStatus status;

    private AddressBookException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static AddressBookException notFound() {
        return new AddressBookException(HttpStatus.NOT_FOUND, "Address not found");
    }

    public static AddressBookException full(int max) {
        return new AddressBookException(HttpStatus.CONFLICT,
                "You can save up to " + max + " addresses. Delete one to add another.");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
