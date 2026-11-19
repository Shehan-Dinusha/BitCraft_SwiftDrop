package com.swiftdrop.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A merchant company id seen on a merchant order (see V10). */
@Entity
@Table(name = "merchant_accounts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAccount {

    @Id
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;
}
