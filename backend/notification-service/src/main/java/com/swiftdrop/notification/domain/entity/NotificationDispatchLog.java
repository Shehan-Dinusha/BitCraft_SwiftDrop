package com.swiftdrop.notification.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_dispatch_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDispatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_id", nullable = false, unique = true, updatable = false)
    private UUID logId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "destination", nullable = false)
    private String destination;

    @Column(name = "subject")
    private String subject;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "SENT";

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.logId == null) {
            this.logId = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.sentAt == null && "SENT".equalsIgnoreCase(this.status)) {
            this.sentAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
