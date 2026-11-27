package com.swiftdrop.webhook.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Legacy dead-letter history written by the synchronous dispatcher that Step 17 retired.
 *
 * <p>Read-only: nothing writes this table any more, and its records cannot be replayed. The read-only list
 * ({@code GET /api/v1/webhooks/dlq}) keeps old rows visible. A job that exhausts its attempts becomes
 * {@link WebhookDeliveryStatus#DEAD} in {@code webhook_deliveries}, listed and replayed by the failed-delivery API.
 * Unrelated to the RabbitMQ inbound dead-letter queue.</p>
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
