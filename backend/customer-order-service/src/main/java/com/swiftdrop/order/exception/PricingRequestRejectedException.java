package com.swiftdrop.order.exception;

/**
 * The pricing service answered with a 4xx: it rejected a request this service had already validated, which
 * points to a contract mismatch between the two services. Not retried. Mapped to 502.
 */
public class PricingRequestRejectedException extends RuntimeException {
    public PricingRequestRejectedException() {
        super("Pricing service rejected the request");
    }
}
