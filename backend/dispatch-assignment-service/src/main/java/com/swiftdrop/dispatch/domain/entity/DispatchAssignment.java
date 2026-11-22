package com.swiftdrop.dispatch.domain.entity;

import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.dispatch.domain.enums.AssignmentLeg;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.parcel.ParcelLimits;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
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

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "customer_id")
    private UUID customerId;

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

    /** When delivery-execution reported the parcel past pickup; reassignment is blocked once set. */
    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "leg", nullable = false, length = 16)
    private AssignmentLeg leg = AssignmentLeg.FIRST_MILE;

    /** Delivery attempts that failed, and why the last one did. */
    @Column(name = "delivery_attempts", nullable = false)
    private Integer deliveryAttempts = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_attempt_reason", length = 32)
    private DeliveryFailureReason lastAttemptReason;

    /** Between the legs: AT_HUB, OUTBOUND or INCOMING. After a failed attempt: RETURN_TO_HUB, HELD_AT_HUB, RETURN_OUTBOUND. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hub_stage", length = 32)
    private InternalQueueStatus hubStage;

    @Column(name = "first_mile_courier_id")
    private UUID firstMileCourierId;

    /** Null for orders booked before sizes were recorded. */
    @Column(name = "length_cm", precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 8, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 8, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "volume_litres", precision = 12, scale = 2)
    private BigDecimal volumeLitres;

    /** The shop's name, known once it has asked for a pickup. */
    @Column(name = "merchant_name", length = 150)
    private String merchantName;

    /** When the shop said the parcel is packed. */
    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "estimated_pickup_minutes")
    private Integer estimatedPickupMinutes = 30;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_order_created_event_id")
    private UUID lastOrderCreatedEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

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

    public Instant getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(Instant pickedUpAt) { this.pickedUpAt = pickedUpAt; }

    public AssignmentLeg getLeg() { return leg; }
    public void setLeg(AssignmentLeg leg) { this.leg = leg; }

    public InternalQueueStatus getHubStage() { return hubStage; }
    public void setHubStage(InternalQueueStatus hubStage) { this.hubStage = hubStage; }

    public int getDeliveryAttempts() { return deliveryAttempts == null ? 0 : deliveryAttempts; }
    public DeliveryFailureReason getLastAttemptReason() { return lastAttemptReason; }

    public void recordFailedAttempt(DeliveryFailureReason reason) {
        deliveryAttempts = getDeliveryAttempts() + 1;
        lastAttemptReason = reason;
    }

    public UUID getFirstMileCourierId() { return firstMileCourierId; }
    public void setFirstMileCourierId(UUID firstMileCourierId) { this.firstMileCourierId = firstMileCourierId; }

    /** The district whose dispatcher manages the order now: pickup on the first leg and on a return, drop-off on the last. */
    public SriLankanDistrict getResponsibleDistrict() {
        return leg == AssignmentLeg.LAST_MILE ? deliveryDistrict : pickupDistrict;
    }

    /** Picked up in one district and delivered in another, so it goes through both hubs. */
    public boolean crossesDistricts() {
        return pickupDistrict != null && deliveryDistrict != null && pickupDistrict != deliveryDistrict;
    }

    public BigDecimal getLengthCm() { return lengthCm; }
    public BigDecimal getWidthCm() { return widthCm; }
    public BigDecimal getHeightCm() { return heightCm; }
    public BigDecimal getWeightKg() { return weightKg; }
    public BigDecimal getVolumeLitres() { return volumeLitres; }

    /** The parcel's size in cm and weight in kg; the volume follows from them. */
    public void setParcel(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, BigDecimal weightKg) {
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.volumeLitres = ParcelLimits.volumeLitres(lengthCm, widthCm, heightCm);
    }

    /** Null when the size is unknown. */
    public BigDecimal getLongestSideCm() {
        return volumeLitres == null ? null : lengthCm.max(widthCm).max(heightCm);
    }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public Instant getReadyAt() { return readyAt; }
    public void setReadyAt(Instant readyAt) { this.readyAt = readyAt; }

    /** Sent by a shop. Such an order carries the shop's id as its customer too; a customer's order never does. */
    public boolean isMerchantOrder() {
        return merchantId != null && merchantId.equals(customerId);
    }

    /**
     * Collected together with the shop's other parcels: a standard merchant order that waits at the shop.
     * Faster services are assigned on their own.
     */
    public boolean isShopPickup() {
        return isMerchantOrder() && leg == AssignmentLeg.FIRST_MILE && orderSpeed == OrderSpeed.STANDARD;
    }

    /** A courier can be sent for it: a shop's parcel only once the shop said it is packed. */
    public boolean isReadyForPickup() {
        return !isShopPickup() || readyAt != null;
    }

    public Integer getEstimatedPickupMinutes() { return estimatedPickupMinutes; }
    public void setEstimatedPickupMinutes(Integer estimatedPickupMinutes) { this.estimatedPickupMinutes = estimatedPickupMinutes; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public UUID getLastOrderCreatedEventId() { return lastOrderCreatedEventId; }
    public void setLastOrderCreatedEventId(UUID lastOrderCreatedEventId) {
        this.lastOrderCreatedEventId = lastOrderCreatedEventId;
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
