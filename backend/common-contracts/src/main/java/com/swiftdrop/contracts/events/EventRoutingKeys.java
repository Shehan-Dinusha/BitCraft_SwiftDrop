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
    /** A booked order was edited before a courier was assigned; dispatch plans it again. */
    public static final String ORDER_UPDATED = "order.updated";

    public static final String PARCEL_STATUS_CHANGED = "parcel.status.changed";
    public static final String PARCEL_FEE_REVISED = "parcel.fee.revised";

    public static final String CASH_TRANSACTION_RECORDED = "cash.transaction.recorded";

    public static final String DISPATCH_COURIER_ASSIGNED = "dispatch.courier.assigned";
    public static final String DISPATCH_FAILED = "dispatch.failed";

    public static final String MERCHANT_APPROVED = "merchant.approved";

    /**
     * A merchant application was submitted, approved or rejected; notification-service alerts the
     * district dispatcher or emails the owner. Under {@code notification.} so the existing
     * {@code notification.#} binding delivers it (IAM keeps consuming {@link #MERCHANT_APPROVED}).
     */
    public static final String MERCHANT_APPLICATION = "notification.merchant.application";

    /**
     * One-time code (email verification / password reset) to be emailed by notification-service.
     * Deliberately under the {@code notification.} namespace so the existing {@code notification.#}
     * binding delivers it; the {@code auth.#} queue has no consumer.
     */
    public static final String AUTH_OTP_REQUESTED = "notification.auth.otp";

    /**
     * A sign-in succeeded or failed with a wrong password; notification-service emails a security alert.
     * Under {@code notification.} for the same reason as {@link #AUTH_OTP_REQUESTED}.
     */
    public static final String AUTH_LOGIN = "notification.auth.login";

    /**
     * A staff account (courier or dispatcher) was invited; notification-service emails the invite link.
     * Under {@code notification.} for the same reason as {@link #AUTH_OTP_REQUESTED}.
     */
    public static final String AUTH_STAFF_INVITE = "notification.auth.invite";
}
