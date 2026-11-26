package com.swiftdrop.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The "Stop these emails" link of one parcel's recipient: the random token it carries, and when the recipient
 * used it. A stopped parcel sends its recipient no more email; the sender's mail is not affected.
 */
@Entity
@Table(name = "recipient_email_stops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipientEmailStop {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    public boolean isStopped() {
        return stoppedAt != null;
    }
}
