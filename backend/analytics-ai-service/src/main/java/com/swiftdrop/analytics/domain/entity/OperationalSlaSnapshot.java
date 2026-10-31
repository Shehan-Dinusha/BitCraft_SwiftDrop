package com.swiftdrop.analytics.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "operational_sla_snapshots")
public class OperationalSlaSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "district", length = 64)
    private SriLankanDistrict district;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_parcels", nullable = false)
    private int totalParcels;

    @Column(name = "delivered_on_time", nullable = false)
    private int deliveredOnTime;

    @Column(name = "breached_sla", nullable = false)
    private int breachedSla;

    @Column(name = "failed_deliveries", nullable = false)
    private int failedDeliveries;

    @Column(name = "avg_transit_time_minutes", precision = 8, scale = 2, nullable = false)
    private BigDecimal avgTransitTimeMinutes = BigDecimal.ZERO;

    @Column(name = "on_time_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal onTimeRate = new BigDecimal("100.00");

    @Column(name = "status", length = 64, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public OperationalSlaSnapshot() {
        this.snapshotDate = LocalDate.now();
    }

    public OperationalSlaSnapshot(SriLankanDistrict district, LocalDate snapshotDate) {
        this.district = district;
        this.snapshotDate = snapshotDate != null ? snapshotDate : LocalDate.now();
        this.status = "ACTIVE";
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SriLankanDistrict getDistrict() { return district; }
    public void setDistrict(SriLankanDistrict district) { this.district = district; }

    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }

    public int getTotalParcels() { return totalParcels; }
    public void setTotalParcels(int totalParcels) { this.totalParcels = totalParcels; }

    public int getDeliveredOnTime() { return deliveredOnTime; }
    public void setDeliveredOnTime(int deliveredOnTime) { this.deliveredOnTime = deliveredOnTime; }

    public int getBreachedSla() { return breachedSla; }
    public void setBreachedSla(int breachedSla) { this.breachedSla = breachedSla; }

    public int getFailedDeliveries() { return failedDeliveries; }
    public void setFailedDeliveries(int failedDeliveries) { this.failedDeliveries = failedDeliveries; }

    public BigDecimal getAvgTransitTimeMinutes() { return avgTransitTimeMinutes; }
    public void setAvgTransitTimeMinutes(BigDecimal avgTransitTimeMinutes) { this.avgTransitTimeMinutes = avgTransitTimeMinutes; }

    public BigDecimal getOnTimeRate() { return onTimeRate; }
    public void setOnTimeRate(BigDecimal onTimeRate) { this.onTimeRate = onTimeRate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
