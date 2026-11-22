package com.swiftdrop.merchant.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_order_receipts", uniqueConstraints = @UniqueConstraint(columnNames = {"merchant_id", "external_ref"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MerchantOrderReceipt {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "merchant_id", nullable = false) private UUID merchantId;
    @Column(name = "external_ref", nullable = false, length = 128) private String externalRef;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "tracking_number", nullable = false, length = 64) private String trackingNumber;
    /** Delivery fee the shop pays at pickup; null for orders from before fees were kept. */
    @Column(name = "fee", precision = 10, scale = 2) private java.math.BigDecimal fee;
    @Column(name = "currency", length = 8) private String currency;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @PrePersist void initialize() { if (createdAt == null) createdAt = Instant.now(); }
}
