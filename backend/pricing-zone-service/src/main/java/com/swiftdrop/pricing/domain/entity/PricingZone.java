package com.swiftdrop.pricing.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pricing_zones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_code", nullable = false, unique = true, length = 32)
    private String zoneCode;

    @Column(name = "zone_name", nullable = false, length = 128)
    private String zoneName;

    @Column(name = "tier", nullable = false, length = 32)
    private String tier;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "per_kg_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKgFee;

    @Column(name = "express_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal expressMultiplier;

    @Column(name = "same_day_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal sameDayMultiplier;

    @Column(name = "status", nullable = false, length = 64)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
