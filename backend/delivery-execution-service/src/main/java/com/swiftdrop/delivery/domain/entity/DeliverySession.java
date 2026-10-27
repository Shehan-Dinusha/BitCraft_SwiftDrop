package com.swiftdrop.delivery.domain.entity;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_sessions")
public class DeliverySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false, unique = true)
    private UUID deliveryId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "tracking_number", nullable = false, length = 32)
    private String trackingNumber;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "courier_name", length = 128)
    private String courierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_district", length = 64)
    private SriLankanDistrict pickupDistrict;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_district", length = 64)
    private SriLankanDistrict deliveryDistrict;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false, length = 32)
    private DeliveryState currentState = DeliveryState.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", length = 32)
    private DeliveryState previousState;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "ACTIVE";

    @Column(name = "failed_reason", length = 512)
    private String failedReason;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "pickup_at")
    private Instant pickupAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "current_latitude", precision = 10, scale = 7)
    private BigDecimal currentLatitude;

    @Column(name = "current_longitude", precision = 10, scale = 7)
    private BigDecimal currentLongitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DeliverySession() {}

    @PrePersist
    public void prePersist() {
        if (deliveryId == null) deliveryId = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (attemptCount == null) attemptCount = 0;
        if (currentState == null) currentState = DeliveryState.CREATED;
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID deliveryId) { this.deliveryId = deliveryId; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public UUID getCourierId() { return courierId; }
    public void setCourierId(UUID courierId) { this.courierId = courierId; }

    public String getCourierName() { return courierName; }
    public void setCourierName(String courierName) { this.courierName = courierName; }

    public SriLankanDistrict getPickupDistrict() { return pickupDistrict; }
    public void setPickupDistrict(SriLankanDistrict pickupDistrict) { this.pickupDistrict = pickupDistrict; }

    public SriLankanDistrict getDeliveryDistrict() { return deliveryDistrict; }
    public void setDeliveryDistrict(SriLankanDistrict deliveryDistrict) { this.deliveryDistrict = deliveryDistrict; }

    public DeliveryState getCurrentState() { return currentState; }
    public void setCurrentState(DeliveryState currentState) { this.currentState = currentState; }

    public DeliveryState getPreviousState() { return previousState; }
    public void setPreviousState(DeliveryState previousState) { this.previousState = previousState; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailedReason() { return failedReason; }
    public void setFailedReason(String failedReason) { this.failedReason = failedReason; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public Instant getPickupAt() { return pickupAt; }
    public void setPickupAt(Instant pickupAt) { this.pickupAt = pickupAt; }

    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }

    public BigDecimal getCurrentLatitude() { return currentLatitude; }
    public void setCurrentLatitude(BigDecimal currentLatitude) { this.currentLatitude = currentLatitude; }

    public BigDecimal getCurrentLongitude() { return currentLongitude; }
    public void setCurrentLongitude(BigDecimal currentLongitude) { this.currentLongitude = currentLongitude; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
