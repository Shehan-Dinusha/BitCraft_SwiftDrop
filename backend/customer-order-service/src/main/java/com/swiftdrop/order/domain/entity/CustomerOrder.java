package com.swiftdrop.order.domain.entity;

import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.UUID;

@Entity
@Table(name = "customer_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    @Builder.Default
    private UUID orderId = UUID.randomUUID();

    @Column(name = "tracking_number", nullable = false, unique = true, length = 32)
    private String trackingNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_speed", nullable = false, length = 32)
    @Builder.Default
    private OrderSpeed orderSpeed = OrderSpeed.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    @Builder.Default
    private PublicOrderStatus status = PublicOrderStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "internal_queue_status", nullable = false, length = 64)
    @Builder.Default
    private InternalQueueStatus internalQueueStatus = InternalQueueStatus.CREATED;

    // Pickup details
    @Column(name = "pickup_address_line1", nullable = false)
    private String pickupAddressLine1;

    @Column(name = "pickup_address_line2")
    private String pickupAddressLine2;

    @Column(name = "pickup_city", nullable = false, length = 128)
    private String pickupCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_district", nullable = false, length = 64)
    private SriLankanDistrict pickupDistrict;

    @Column(name = "pickup_postal_code", nullable = false, length = 20)
    private String pickupPostalCode;

    @Column(name = "pickup_contact_name", nullable = false, length = 128)
    private String pickupContactName;

    @Column(name = "pickup_contact_phone", nullable = false, length = 32)
    private String pickupContactPhone;

    // Delivery details
    @Column(name = "delivery_address_line1", nullable = false)
    private String deliveryAddressLine1;

    @Column(name = "delivery_address_line2")
    private String deliveryAddressLine2;

    @Column(name = "delivery_city", nullable = false, length = 128)
    private String deliveryCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_district", nullable = false, length = 64)
    private SriLankanDistrict deliveryDistrict;

    @Column(name = "delivery_postal_code", nullable = false, length = 20)
    private String deliveryPostalCode;

    @Column(name = "recipient_name", nullable = false, length = 128)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 32)
    private String recipientPhone;

    @Column(name = "recipient_email", nullable = false, length = 128)
    private String recipientEmail;

    // Physical dimensions & weight
    @Column(name = "length_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "actual_weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal actualWeightKg;

    @Column(name = "chargeable_weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal chargeableWeightKg;

    @Column(name = "item_description", length = 512)
    private String itemDescription;

    @Column(name = "declared_value", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal declaredValue = BigDecimal.ZERO;

    @Column(name = "cod_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal codAmount = BigDecimal.ZERO;

    @Column(name = "prohibited_items_declared", nullable = false)
    @Builder.Default
    private Boolean prohibitedItemsDeclared = false;

    // Pricing & fee
    @Column(name = "pricing_rule_version_id", nullable = false, length = 64)
    private String pricingRuleVersionId;

    @Column(name = "estimated_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedFee;

    @Column(name = "final_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalFee;

    @Column(name = "currency", nullable = false, length = 8)
    @Builder.Default
    private String currency = "LKR";

    // Fee revision workflow (FR-C8, FR-K5)
    @Enumerated(EnumType.STRING)
    @Column(name = "fee_revision_status", length = 32)
    @Builder.Default
    private FeeRevisionStatus feeRevisionStatus = FeeRevisionStatus.APPROVED;

    @Column(name = "revised_fee", precision = 10, scale = 2)
    private BigDecimal revisedFee;

    @Column(name = "fee_revision_reason", length = 512)
    private String feeRevisionReason;

    // Cancellation (FR-C3)
    @Column(name = "cancellation_reason", length = 512)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
