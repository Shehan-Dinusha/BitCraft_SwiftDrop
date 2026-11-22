package com.swiftdrop.delivery.exception;

/** The order service could not be reached or gave no usable answer. */
public class OrderDetailsUnavailableException extends RuntimeException {

    public OrderDetailsUnavailableException() {
        super("The addresses could not be loaded. Try again.");
    }
}
