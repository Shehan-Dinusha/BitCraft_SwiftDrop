package com.swiftdrop.webhook.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Legacy dead-letter history written by the synchronous {@code OutboundWebhookDispatcher}.
 *
 * <p>Kept because the existing DLQ list/replay API and the operations console read it. It is not the
 * source of truth for delivery jobs: a job that exhausts its attempts becomes
 * {@link WebhookDeliveryStatus#DEAD} in {@code webhook_deliveries}. Serving dead jobs through the DLQ API
 * (a read model over {@code webhook_deliveries}) belongs to the replay redesign.</p>
 */
@Entity
@Table(name = "dead_letter_webhook_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterWebhookRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dlq_id", nullable = false, unique = true, updatable = false)
    private UUID dlqId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "total_attempts", nullable = false)
    @Builder.Default
    private int totalAttempts = 3;

    @Column(name = "is_replayed", nullable = false)
    @Builder.Default
    private boolean replayed = false;

    @Column(name = "replayed_at")
    private Instant replayedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.dlqId == null) {
            this.dlqId = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
