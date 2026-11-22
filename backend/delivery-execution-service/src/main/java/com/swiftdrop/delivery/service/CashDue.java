package com.swiftdrop.delivery.service;

import java.math.BigDecimal;
import java.util.UUID;

/** The delivery fee the courier has just confirmed collecting. */
public record CashDue(BigDecimal amount, String currency, UUID paymentId) {

    /** Collected on its own: a shop's parcel gets a payment of its own. */
    public CashDue(BigDecimal amount, String currency) {
        this(amount, currency, null);
    }
}
