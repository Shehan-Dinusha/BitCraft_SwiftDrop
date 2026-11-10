package com.swiftdrop.order.domain.entity;

/**
 * Where an order came from. For {@link #MERCHANT} orders the customer id is the merchant id
 * (the convention the merchant service already uses).
 */
public enum OrderSource {
    CUSTOMER,
    MERCHANT
}
