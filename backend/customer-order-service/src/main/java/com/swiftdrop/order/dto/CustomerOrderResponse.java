package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.FeeRevisionStatus;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer order details response")
public class CustomerOrderResponse {

    @Schema(description = "Internal database ID", example = "1")
    private Long id;

    @Schema(description = "Order UUID", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID orderId;

    @Schema(description = "Non-sequential tracking number", example = "SD-7K4M-9QX2")
    private String trackingNumber;

    @Schema(description = "Customer UUID", example = "b1ffbc99-9c0b-4ef8-bb6d-6bb9bd380b22")
    private UUID customerId;

    @Schema(description = "Merchant UUID", example = "c2eebc99-9c0b-4ef8-bb6d-6bb9bd380c33")
    private UUID merchantId;

    @Schema(description = "Delivery service speed level", example = "STANDARD")
    private OrderSpeed orderSpeed;

    @Schema(description = "Public order status", example = "CREATED")
    private PublicOrderStatus status;

    @Schema(description = "Internal operations queue status", example = "CREATED")
    private InternalQueueStatus internalQueueStatus;

    // Pickup
    @Schema(description = "Pickup address line 1", example = "No. 42, Galle Road")
    private String pickupAddressLine1;

    @Schema(description = "Pickup address line 2", example = "Suite 4B")
    private String pickupAddressLine2;

    @Schema(description = "Pickup city", example = "Colombo 03")
    private String pickupCity;

    @Schema(description = "Pickup district", example = "COLOMBO")
    private SriLankanDistrict pickupDistrict;

    @Schema(description = "Pickup postal code", example = "00300")
    private String pickupPostalCode;

    @Schema(description = "Pickup contact name", example = "Sunil Perera")
    private String pickupContactName;

    @Schema(description = "Pickup contact phone", example = "+94771234567")
    private String pickupContactPhone;

    // Delivery
    @Schema(description = "Delivery address line 1", example = "No. 18, Peradeniya Road")
    private String deliveryAddressLine1;

    @Schema(description = "Delivery address line 2", example = "Hilltop Residence")
    private String deliveryAddressLine2;

    @Schema(description = "Delivery city", example = "Kandy")
    private String deliveryCity;

    @Schema(description = "Delivery district", example = "KANDY")
    private SriLankanDistrict deliveryDistrict;

    @Schema(description = "Delivery postal code", example = "20000")
    private String deliveryPostalCode;

    @Schema(description = "Recipient name", example = "Anura Fernando")
    private String recipientName;

    @Schema(description = "Recipient phone", example = "+94719876543")
    private String recipientPhone;

    @Schema(description = "Recipient email", example = "anura.f@gmail.com")
    private String recipientEmail;

    // Physical dimensions & weight
    @Schema(description = "Package length in cm", example = "25.0")
    private BigDecimal lengthCm;

    @Schema(description = "Package width in cm", example = "20.0")
    private BigDecimal widthCm;

    @Schema(description = "Package height in cm", example = "10.0")
    private BigDecimal heightCm;

    @Schema(description = "Package actual weight in kg", example = "1.50")
    private BigDecimal actualWeightKg;

    @Schema(description = "Package chargeable billable weight in kg", example = "1.50")
    private BigDecimal chargeableWeightKg;

    @Schema(description = "Item description", example = "Handicrafts and tea canisters")
    private String itemDescription;

    @Schema(description = "Declared merchandise value in LKR", example = "5000.00")
    private BigDecimal declaredValue;

    @Schema(description = "COD amount in LKR", example = "0.00")
    private BigDecimal codAmount;

    @Schema(description = "Prohibited items declaration flag", example = "true")
    private Boolean prohibitedItemsDeclared;

    // Pricing
    @Schema(description = "Locked pricing rule version ID", example = "PRICING-2026-V1")
    private String pricingRuleVersionId;

    @Schema(description = "Estimated fee in LKR", example = "610.00")
    private BigDecimal estimatedFee;

    @Schema(description = "Final fee in LKR", example = "610.00")
    private BigDecimal finalFee;

    @Schema(description = "Currency", example = "LKR")
    private String currency;

    // Fee revision
    @Schema(description = "Fee revision status (FR-C8)", example = "APPROVED")
    private FeeRevisionStatus feeRevisionStatus;

    @Schema(description = "Revised fee submitted by courier in LKR", example = "750.00")
    private BigDecimal revisedFee;

    @Schema(description = "Fee revision reason given by courier", example = "Actual weight is 3.2kg exceeding booking declaration")
    private String feeRevisionReason;

    // Cancellation
    @Schema(description = "Cancellation reason if cancelled", example = "Cancelled by sender")
    private String cancellationReason;

    @Schema(description = "Cancellation timestamp")
    private Instant cancelledAt;

    @Schema(description = "Created timestamp")
    private Instant createdAt;

    @Schema(description = "Updated timestamp")
    private Instant updatedAt;

    public static CustomerOrderResponse fromEntity(CustomerOrder order) {
        if (order == null) return null;
        return CustomerOrderResponse.builder()
                .id(order.getId())
                .orderId(order.getOrderId())
                .trackingNumber(order.getTrackingNumber())
                .customerId(order.getCustomerId())
                .merchantId(order.getMerchantId())
                .orderSpeed(order.getOrderSpeed())
                .status(order.getStatus())
                .internalQueueStatus(order.getInternalQueueStatus())
                .pickupAddressLine1(order.getPickupAddressLine1())
                .pickupAddressLine2(order.getPickupAddressLine2())
                .pickupCity(order.getPickupCity())
                .pickupDistrict(order.getPickupDistrict())
                .pickupPostalCode(order.getPickupPostalCode())
                .pickupContactName(order.getPickupContactName())
                .pickupContactPhone(order.getPickupContactPhone())
                .deliveryAddressLine1(order.getDeliveryAddressLine1())
                .deliveryAddressLine2(order.getDeliveryAddressLine2())
                .deliveryCity(order.getDeliveryCity())
                .deliveryDistrict(order.getDeliveryDistrict())
                .deliveryPostalCode(order.getDeliveryPostalCode())
                .recipientName(order.getRecipientName())
                .recipientPhone(order.getRecipientPhone())
                .recipientEmail(order.getRecipientEmail())
                .lengthCm(order.getLengthCm())
                .widthCm(order.getWidthCm())
                .heightCm(order.getHeightCm())
                .actualWeightKg(order.getActualWeightKg())
                .chargeableWeightKg(order.getChargeableWeightKg())
                .itemDescription(order.getItemDescription())
                .declaredValue(order.getDeclaredValue())
                .codAmount(order.getCodAmount())
                .prohibitedItemsDeclared(order.getProhibitedItemsDeclared())
                .pricingRuleVersionId(order.getPricingRuleVersionId())
                .estimatedFee(order.getEstimatedFee())
                .finalFee(order.getFinalFee())
                .currency(order.getCurrency())
                .feeRevisionStatus(order.getFeeRevisionStatus())
                .revisedFee(order.getRevisedFee())
                .feeRevisionReason(order.getFeeRevisionReason())
                .cancellationReason(order.getCancellationReason())
                .cancelledAt(order.getCancelledAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
