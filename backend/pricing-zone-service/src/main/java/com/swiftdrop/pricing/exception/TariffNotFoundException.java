package com.swiftdrop.pricing.exception;

public class TariffNotFoundException extends RuntimeException {

    public TariffNotFoundException(String message) {
        super(message);
    }
}