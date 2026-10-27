package com.swiftdrop.merchant.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bulk_batch_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkBatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 30)
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_district", nullable = false, length = 50)
    private SriLankanDistrict deliveryDistrict;

    @Column(name = "delivery_address", nullable = false, length = 255)
    private String deliveryAddress;

    @Column(name = "package_weight_kg", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal packageWeightKg = BigDecimal.valueOf(1.0);

    @Column(name = "delivery_speed", nullable = false, length = 50)
    @Builder.Default
    private String deliverySpeed = "STANDARD";

    @Column(name = "item_status", nullable = false, length = 50)
    @Builder.Default
    private String itemStatus = "PENDING";

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.itemStatus == null) {
            this.itemStatus = "PENDING";
        }
        if (this.deliverySpeed == null) {
            this.deliverySpeed = "STANDARD";
        }
    }
}
