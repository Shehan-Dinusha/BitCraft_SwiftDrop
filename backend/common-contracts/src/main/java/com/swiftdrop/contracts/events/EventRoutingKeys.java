package com.swiftdrop.contracts.events;

/**
 * Canonical RabbitMQ routing keys for SwiftDrop domain events.
 * The shared topic exchange remains {@code swiftdrop.events.exchange}.
 */
public final class EventRoutingKeys {

    private EventRoutingKeys() {
    }

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CANCELLED = "order.cancelled";

    public static final String PARCEL_STATUS_CHANGED = "parcel.status.changed";
    public static final String PARCEL_FEE_REVISED = "parcel.fee.revised";

    public static final String CASH_TRANSACTION_RECORDED = "cash.transaction.recorded";

    public static final String DISPATCH_COURIER_ASSIGNED = "dispatch.courier.assigned";
    public static final String DISPATCH_FAILED = "dispatch.failed";
}
