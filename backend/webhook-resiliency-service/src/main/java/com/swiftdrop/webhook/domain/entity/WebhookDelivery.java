package com.swiftdrop.webhook.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One delivery of one inbound event to one webhook endpoint: the unit of work for the delivery worker.
 *
 * <p>Ingestion creates these rows with {@code INSERT ... ON CONFLICT DO NOTHING} against the partial
 * unique index on {@code (inbound_event_id, endpoint_id)} for originals, so concurrent consumers and
 * broker redeliveries cannot create a second job for the same event and endpoint. Composite foreign keys
 * guarantee that {@link #merchantId} equals both the event's resolved merchant and the endpoint's owner.</p>
 *
 * <p>{@link #version} guards worker state changes made through JPA. Claiming with
 * {@code FOR UPDATE SKIP LOCKED}, leases and retries belong to the delivery-worker phase.</p>
 */
@Entity
@Table(name = "webhook_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookDelivery {

    @Id
    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inbound_event_id", nullable = false, updatable = false)
    private InboundEvent inboundEvent;

    @Column(name = "endpoint_id", nullable = false, updatable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WebhookDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    /** Set on replay rows only; points at the delivery being replayed. */
    @Column(name = "replay_of_delivery_id", updatable = false)
    private UUID replayOfDeliveryId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
