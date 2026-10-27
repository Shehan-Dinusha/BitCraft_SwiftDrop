package com.swiftdrop.dispatch.domain.entity;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatch_assignments")
public class DispatchAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false, unique = true)
    private UUID assignmentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "tracking_number", length = 32)
    private String trackingNumber;

    @Column(name = "courier_id")
    private UUID courierId;

    @Column(name = "courier_name", length = 128)
    private String courierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_district")
    private SriLankanDistrict pickupDistrict;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_district")
    private SriLankanDistrict deliveryDistrict;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_speed")
    private OrderSpeed orderSpeed = OrderSpeed.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AssignmentStatus status = AssignmentStatus.UNASSIGNED;

    @Column(name = "assigned_by", length = 32)
    private String assignedBy = "AUTO_ENGINE";

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "estimated_pickup_minutes")
    private Integer estimatedPickupMinutes = 30;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DispatchAssignment() {}

    @PrePersist
    public void prePersist() {
        if (assignmentId == null) {
            assignmentId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (status == null) {
            status = AssignmentStatus.UNASSIGNED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }

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

    public OrderSpeed getOrderSpeed() { return orderSpeed; }
    public void setOrderSpeed(OrderSpeed orderSpeed) { this.orderSpeed = orderSpeed; }

    public AssignmentStatus getStatus() { return status; }
    public void setStatus(AssignmentStatus status) { this.status = status; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }

    public Integer getEstimatedPickupMinutes() { return estimatedPickupMinutes; }
    public void setEstimatedPickupMinutes(Integer estimatedPickupMinutes) { this.estimatedPickupMinutes = estimatedPickupMinutes; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
