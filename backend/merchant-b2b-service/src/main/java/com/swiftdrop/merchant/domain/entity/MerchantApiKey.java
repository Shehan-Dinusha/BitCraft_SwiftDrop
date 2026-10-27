package com.swiftdrop.merchant.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_id", nullable = false, unique = true, updatable = false)
    private UUID keyId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "key_name", nullable = false, length = 100)
    private String keyName;

    @Column(name = "key_prefix", nullable = false, length = 24)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    public void prePersist() {
        if (this.keyId == null) {
            this.keyId = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
