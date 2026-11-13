package com.swiftdrop.pricing.exception;

public class ZoneNotFoundException extends RuntimeException {

    public ZoneNotFoundException(String zoneCode) {
        super("Pricing zone not found: " + zoneCode);
    }
}
