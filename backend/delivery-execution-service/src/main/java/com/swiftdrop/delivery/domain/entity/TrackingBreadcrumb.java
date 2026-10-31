package com.swiftdrop.delivery.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracking_breadcrumbs")
public class TrackingBreadcrumb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "bearing_degrees", precision = 5, scale = 2)
    private BigDecimal bearingDegrees = BigDecimal.ZERO;

    @Column(name = "speed_kmh", precision = 5, scale = 2)
    private BigDecimal speedKmh = BigDecimal.ZERO;

    @Column(name = "accuracy", precision = 6, scale = 2)
    private BigDecimal accuracy = BigDecimal.valueOf(5.0);

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public TrackingBreadcrumb() {}

    @PrePersist
    public void prePersist() {
        if (recordedAt == null) recordedAt = Instant.now();
        if (bearingDegrees == null) bearingDegrees = BigDecimal.ZERO;
        if (speedKmh == null) speedKmh = BigDecimal.ZERO;
        if (accuracy == null) accuracy = BigDecimal.valueOf(5.0);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID deliveryId) { this.deliveryId = deliveryId; }

    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID courierId) { this.courierId = courierId; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getBearingDegrees() { return bearingDegrees; }
    public void setBearingDegrees(BigDecimal bearingDegrees) { this.bearingDegrees = bearingDegrees; }

    public BigDecimal getSpeedKmh() { return speedKmh; }
    public void setSpeedKmh(BigDecimal speedKmh) { this.speedKmh = speedKmh; }

    public BigDecimal getAccuracy() { return accuracy; }
    public void setAccuracy(BigDecimal accuracy) { this.accuracy = accuracy; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
