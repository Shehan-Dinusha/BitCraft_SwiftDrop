package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.statemachine.HubRoute;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Details of an active or completed delivery session.")
public record DeliverySessionResponse(
        @Schema(description = "Delivery session UUID")
        UUID deliveryId,

        @Schema(description = "Order UUID")
        UUID orderId,

        @Schema(description = "Public tracking number", example = "SD-COL-98214")
        String trackingNumber,

        @Schema(description = "Assigned courier UUID")
        UUID courierId,

        @Schema(description = "Assigned courier name")
        String courierName,

        @Schema(description = "Pickup district")
        SriLankanDistrict pickupDistrict,

        @Schema(description = "Destination delivery district")
        SriLankanDistrict deliveryDistrict,

        @Schema(description = "Current FSM delivery state", example = "IN_TRANSIT")
        DeliveryState currentState,

        @Schema(description = "Previous FSM delivery state")
        DeliveryState previousState,

        @Schema(description = "Why the last attempt or the delivery failed")
        String failedReason,

        @Schema(description = "Delivery attempts that failed")
        Integer attemptCount,

        @Schema(description = "Timestamp when package was picked up")
        Instant pickupAt,

        @Schema(description = "Timestamp when package was delivered")
        Instant deliveredAt,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last updated timestamp")
        Instant updatedAt,

        @Schema(description = "The shop that sent it, so its parcels are collected together; null for a customer's parcel")
        UUID merchantId,

        @Schema(description = "Shop name; null when not known", example = "Nimal Fashion")
        String merchantName,

        @Schema(description = "Proof from before this moment belongs to an earlier attempt and does not count; null on a first attempt")
        Instant proofSince,

        @Schema(description = "When the sender got the parcel back")
        Instant returnedAt,

        @Schema(description = "The order's speed; null on older deliveries")
        OrderSpeed speed,

        @Schema(description = "Whether the courier hands it in at the hub after pickup (Standard, Express, or another "
                + "district) instead of taking it straight to the recipient (Same day in one district)")
        boolean viaHub
) {
    /** For callers without a shop. */
    public DeliverySessionResponse(UUID deliveryId, UUID orderId, String trackingNumber, UUID courierId, String courierName,
                                   SriLankanDistrict pickupDistrict, SriLankanDistrict deliveryDistrict,
                                   DeliveryState currentState, DeliveryState previousState, String failedReason,
                                   Integer attemptCount, Instant pickupAt, Instant deliveredAt, Instant createdAt,
                                   Instant updatedAt) {
        this(deliveryId, orderId, trackingNumber, courierId, courierName, pickupDistrict, deliveryDistrict, currentState,
                previousState, failedReason, attemptCount, pickupAt, deliveredAt, createdAt, updatedAt, null, null, null, null,
                null, pickupDistrict != null && deliveryDistrict != null && pickupDistrict != deliveryDistrict);
    }

    /**
     * How a cross-district delivery looks to the courier who handed it in: their part ended at the hub, and
     * nothing after it (the next courier, the delivery) is theirs to see.
     */
    public static DeliverySessionResponse handedIn(DeliverySession entity) {
        return new DeliverySessionResponse(entity.getDeliveryId(), entity.getOrderId(), entity.getTrackingNumber(),
                entity.getFirstMileCourierId(), null, entity.getPickupDistrict(), entity.getDeliveryDistrict(),
                DeliveryState.AT_ORIGIN_HUB, DeliveryState.PICKED_UP, null, entity.getAttemptCount(),
                entity.getPickupAt(), null, entity.getCreatedAt(), entity.getHandedInAt());
    }

    public static DeliverySessionResponse fromEntity(DeliverySession entity) {
        if (entity == null) return null;
        return new DeliverySessionResponse(
                entity.getDeliveryId(),
                entity.getOrderId(),
                entity.getTrackingNumber(),
                entity.getCourierId(),
                entity.getCourierName(),
                entity.getPickupDistrict(),
                entity.getDeliveryDistrict(),
                entity.getCurrentState(),
                entity.getPreviousState(),
                entity.getFailedReason(),
                entity.getAttemptCount(),
                entity.getPickupAt(),
                entity.getDeliveredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getMerchantId(),
                entity.getMerchantName(),
                entity.getProofSince(),
                entity.getReturnedAt(),
                entity.getOrderSpeed(),
                HubRoute.goesViaHub(entity)
        );
    }
}
