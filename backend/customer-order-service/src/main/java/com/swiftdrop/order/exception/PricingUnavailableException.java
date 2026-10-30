package com.swiftdrop.order.exception;

/**
 * The pricing service could not provide a fee: it is unreachable, too slow, failing, or returned an
 * unusable response. Mapped to 503. The message is fixed and never carries the downstream URL or body.
 */
public class PricingUnavailableException extends RuntimeException {
    public PricingUnavailableException() {
        super("Pricing service is temporarily unavailable");
    }

    public PricingUnavailableException(Throwable cause) {
        super("Pricing service is temporarily unavailable", cause);
    }
}
