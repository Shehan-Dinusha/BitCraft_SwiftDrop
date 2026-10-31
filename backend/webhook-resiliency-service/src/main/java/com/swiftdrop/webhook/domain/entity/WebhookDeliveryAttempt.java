package com.swiftdrop.webhook.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

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

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

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
