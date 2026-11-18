package com.swiftdrop.order.exception;

/**
 * Raised when an order PDF (waybill, receipt or label) cannot be rendered.
 * The global handler maps it to a generic 500 so no rendering detail reaches the client.
 */
public class OrderDocumentGenerationException extends RuntimeException {

    public OrderDocumentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
