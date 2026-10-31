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
@Table(name = "tariff_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TariffRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_version_id", nullable = false, length = 64)
    private String ruleVersionId;

    @Column(name = "from_district", nullable = false, length = 64)
    private String fromDistrict;

    @Column(name = "to_district", nullable = false, length = 64)
    private String toDistrict;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "weight_step_kg", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal weightStepKg = BigDecimal.ONE;

    @Column(name = "price_per_step", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal pricePerStep = new BigDecimal("100.00");

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
