package com.swiftdrop.analytics.domain.entity;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courier_performance_metrics")
public class CourierPerformanceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "courier_id", nullable = false, unique = true)
    private UUID courierId;

    @Column(name = "courier_name", nullable = false)
    private String courierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_district", nullable = false, length = 64)
    private SriLankanDistrict currentDistrict;

    @Column(name = "total_assigned", nullable = false)
    private int totalAssigned;

    @Column(name = "delivered_count", nullable = false)
    private int deliveredCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "avg_transit_minutes", precision = 8, scale = 2, nullable = false)
    private BigDecimal avgTransitMinutes = BigDecimal.ZERO;

    @Column(name = "on_time_percentage", precision = 5, scale = 2, nullable = false)
    private BigDecimal onTimePercentage = new BigDecimal("100.00");

    @Column(name = "customer_rating", precision = 3, scale = 2, nullable = false)
    private BigDecimal customerRating = new BigDecimal("5.00");

    @Column(name = "status", nullable = false, length = 64)
    private String status = "AVAILABLE";

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CourierPerformanceMetric() {}

    public CourierPerformanceMetric(UUID courierId, String courierName, SriLankanDistrict currentDistrict) {
        this.courierId = courierId;
        this.courierName = courierName != null ? courierName : "Courier-" + courierId.toString().substring(0, 8);
        this.currentDistrict = currentDistrict != null ? currentDistrict : SriLankanDistrict.COLOMBO;
        this.status = "AVAILABLE";
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID courierId) { this.courierId = courierId; }

    public String getCourierName() { return courierName; }
    public void setCourierName(String courierName) { this.courierName = courierName; }

    public SriLankanDistrict getCurrentDistrict() { return currentDistrict; }
    public void setCurrentDistrict(SriLankanDistrict currentDistrict) { this.currentDistrict = currentDistrict; }

    public int getTotalAssigned() { return totalAssigned; }
    public void setTotalAssigned(int totalAssigned) { this.totalAssigned = totalAssigned; }

    public int getDeliveredCount() { return deliveredCount; }
    public void setDeliveredCount(int deliveredCount) { this.deliveredCount = deliveredCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public BigDecimal getAvgTransitMinutes() { return avgTransitMinutes; }
    public void setAvgTransitMinutes(BigDecimal avgTransitMinutes) { this.avgTransitMinutes = avgTransitMinutes; }

    public BigDecimal getOnTimePercentage() { return onTimePercentage; }
    public void setOnTimePercentage(BigDecimal onTimePercentage) { this.onTimePercentage = onTimePercentage; }

    public BigDecimal getCustomerRating() { return customerRating; }
    public void setCustomerRating(BigDecimal customerRating) { this.customerRating = customerRating; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastEventAt() { return lastEventAt; }
    public void setLastEventAt(Instant lastEventAt) { this.lastEventAt = lastEventAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
