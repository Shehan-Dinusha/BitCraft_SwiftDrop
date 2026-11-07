package com.swiftdrop.pricing.exception;

import com.swiftdrop.contracts.enums.OrderSpeed;

public class UnsupportedPricingSpeedException extends RuntimeException {

    public UnsupportedPricingSpeedException(OrderSpeed speed) {
        super("No pricing strategy is available for speed: " + speed);
    }
}
