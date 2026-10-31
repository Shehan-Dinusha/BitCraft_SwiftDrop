package com.swiftdrop.notification.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipient_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, unique = true)
    private UUID recipientId;

    @Column(name = "recipient_type", nullable = false, length = 32)
    @Builder.Default
    private String recipientType = "CUSTOMER";

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    @Builder.Default
    private boolean smsEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = true;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "language_preference", nullable = false, length = 8)
    @Builder.Default
    private String languagePreference = "en";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.recipientType == null) {
            this.recipientType = "CUSTOMER";
        }
        if (this.languagePreference == null) {
            this.languagePreference = "en";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
