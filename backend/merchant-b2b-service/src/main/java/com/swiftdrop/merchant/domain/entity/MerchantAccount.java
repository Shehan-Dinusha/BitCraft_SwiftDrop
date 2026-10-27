package com.swiftdrop.merchant.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false, unique = true, updatable = false)
    private UUID merchantId;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "pickup_address", length = 255)
    private String pickupAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "operating_district", nullable = false, length = 50)
    private SriLankanDistrict operatingDistrict;

    @Column(name = "status", nullable = false, length = 64)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.merchantId == null) {
            this.merchantId = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
        if (this.operatingDistrict == null) {
            this.operatingDistrict = SriLankanDistrict.COLOMBO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
