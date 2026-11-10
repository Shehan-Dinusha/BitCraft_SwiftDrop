package com.swiftdrop.webhook.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One HTTP attempt.
 *
 * <p>Two shapes coexist (enforced by {@code ck_webhook_delivery_attempts_model}):</p>
 * <ul>
 *   <li><b>Job attempt</b>: {@link #deliveryId} set, {@link #payload} null. The payload is read through
 *       the delivery's inbound event instead of being copied into every attempt.</li>
 *   <li><b>Legacy attempt</b>: {@link #deliveryId} null, own {@link #payload} copy. Written before V3 and,
 *       until they are migrated to delivery jobs, by test pings and legacy dead-letter replays.
 *       Legacy failures recorded HTTP 500 regardless of the real outcome.</li>
 * </ul>
 */
@Entity
@Table(name = "webhook_delivery_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "attempt_id", nullable = false, unique = true, updatable = false)
    private UUID attemptId;

    @Column(name = "delivery_id", updatable = false)
    private UUID deliveryId;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_class", length = 32)
    private AttemptErrorClass errorClass;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private int attemptNumber = 1;

    @Column(name = "is_success", nullable = false)
    @Builder.Default
    private boolean success = false;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @PrePersist
    public void prePersist() {
        if (this.attemptId == null) {
            this.attemptId = UUID.randomUUID();
        }
        if (this.attemptedAt == null) {
            this.attemptedAt = Instant.now();
        }
    }
}
