package com.swiftdrop.webhook.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Local projection of order ownership, built from {@code order.created}.
 *
 * <p>Keyed by the canonical order id: the {@code orderId} of {@code order.created}, which
 * dispatch-assignment and delivery-execution carry unchanged into every {@code parcel.status.changed}.
 * The tracking number is <em>not</em> a usable key, because delivery-execution publishes its own.
 * A {@code null} merchant means the order exists but was placed by a customer, not a merchant.</p>
 *
 * <p>Rows are written only through {@code IngestionJdbcRepository} (insert-if-absent); the first
 * {@code order.created} for an order wins and a later conflicting claim is rejected.</p>
 */
@Entity
@Table(name = "order_merchant_map")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderMerchantMapping {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "merchant_id", updatable = false)
    private UUID merchantId;

    @Column(name = "tracking_number", length = 64, updatable = false)
    private String trackingNumber;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
