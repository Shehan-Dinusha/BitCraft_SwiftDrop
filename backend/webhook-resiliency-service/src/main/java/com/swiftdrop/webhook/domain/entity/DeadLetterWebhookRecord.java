package com.swiftdrop.webhook.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

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
