package com.swiftdrop.pricing.domain.entity;

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

@Entity
@Table(name = "district_zone_mappings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictZoneMapping {

    @Id
    @Column(name = "district", length = 64)
    private String district;

    @Column(name = "zone_code", nullable = false, length = 32)
    private String zoneCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
