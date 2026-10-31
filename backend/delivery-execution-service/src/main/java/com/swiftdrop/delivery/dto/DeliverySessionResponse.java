package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
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

        @Schema(description = "Failure reason if FAILED")
        String failedReason,

        @Schema(description = "Delivery attempt count")
        Integer attemptCount,

        @Schema(description = "Timestamp when package was picked up")
        Instant pickupAt,

        @Schema(description = "Timestamp when package was delivered")
        Instant deliveredAt,

        @Schema(description = "Last known courier latitude")
        BigDecimal currentLatitude,

        @Schema(description = "Last known courier longitude")
        BigDecimal currentLongitude,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last updated timestamp")
        Instant updatedAt
) {
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
                entity.getCurrentLatitude(),
                entity.getCurrentLongitude(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
