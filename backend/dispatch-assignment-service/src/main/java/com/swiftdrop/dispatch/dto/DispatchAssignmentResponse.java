package com.swiftdrop.dispatch.dto;

import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.dispatch.domain.enums.AssignmentLeg;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Details of an order dispatch assignment.")
public record DispatchAssignmentResponse(
        @Schema(description = "Unique dispatch assignment ID")
        UUID assignmentId,

        @Schema(description = "Customer order ID")
        UUID orderId,

        @Schema(description = "Tracking number", example = "SD-COL-98214")
        String trackingNumber,

        @Schema(description = "Assigned courier UUID")
        UUID courierId,

        @Schema(description = "Courier name")
        String courierName,

        @Schema(description = "Pickup district")
        SriLankanDistrict pickupDistrict,

        @Schema(description = "Delivery destination district")
        SriLankanDistrict deliveryDistrict,

        @Schema(description = "Delivery service speed")
        OrderSpeed orderSpeed,

        @Schema(description = "Assignment lifecycle status")
        AssignmentStatus status,

        @Schema(description = "Actor who triggered assignment (AUTO_ENGINE or DISPATCHER)")
        String assignedBy,

        @Schema(description = "Timestamp when assignment occurred")
        Instant assignedAt,

        @Schema(description = "Estimated pickup window in minutes")
        Integer estimatedPickupMinutes,

        @Schema(description = "Failure reason if unassigned or failed")
        String failureReason,

        @Schema(description = "Number of dispatch attempts")
        Integer retryCount,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last update timestamp")
        Instant updatedAt,

        @Schema(description = "FIRST_MILE (pickup district), LAST_MILE (from the drop-off district's hub) or RETURN (back to the sender)")
        AssignmentLeg leg,

        @Schema(description = "Between the legs: AT_HUB, OUTBOUND or INCOMING; after a failed attempt: RETURN_TO_HUB, HELD_AT_HUB, RETURN_OUTBOUND or RETURN_AT_HUB")
        InternalQueueStatus hubStage,

        @Schema(description = "The district whose dispatcher manages the order now")
        SriLankanDistrict responsibleDistrict,

        @Schema(description = "When the courier of the current leg collected the parcel; reassignment is blocked once set")
        Instant pickedUpAt,

        @Schema(description = "Parcel length in cm; null for orders booked before sizes were recorded", example = "30")
        BigDecimal lengthCm,

        @Schema(description = "Parcel width in cm", example = "20")
        BigDecimal widthCm,

        @Schema(description = "Parcel height in cm", example = "10")
        BigDecimal heightCm,

        @Schema(description = "Parcel weight in kg", example = "2.5")
        BigDecimal weightKg,

        @Schema(description = "The shop, for an order it sent; null otherwise")
        UUID merchantId,

        @Schema(description = "The shop's name; null until it first asked for a pickup", example = "Nimal Fashion")
        String merchantName,

        @Schema(description = "Collected together with the shop's other parcels (a standard order still at the shop)")
        boolean shopPickup,

        @Schema(description = "Whether a courier can be sent: a shop's parcel only once the shop said it is packed")
        boolean readyForPickup,

        @Schema(description = "When the shop said the parcel is packed")
        Instant readyAt,

        @Schema(description = "Delivery attempts that failed", example = "1")
        int deliveryAttempts,

        @Schema(description = "Why the last attempt failed")
        DeliveryFailureReason lastAttemptReason,

        @Schema(description = "Whether it may still go out for another attempt")
        boolean canRetry
) {
    public static DispatchAssignmentResponse fromEntity(DispatchAssignment entity) {
        if (entity == null) return null;
        return new DispatchAssignmentResponse(
                entity.getAssignmentId(),
                entity.getOrderId(),
                entity.getTrackingNumber(),
                entity.getCourierId(),
                entity.getCourierName(),
                entity.getPickupDistrict(),
                entity.getDeliveryDistrict(),
                entity.getOrderSpeed(),
                entity.getStatus(),
                entity.getAssignedBy(),
                entity.getAssignedAt(),
                entity.getEstimatedPickupMinutes(),
                entity.getFailureReason(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLeg(),
                entity.getHubStage(),
                entity.getResponsibleDistrict(),
                entity.getPickedUpAt(),
                entity.getLengthCm(),
                entity.getWidthCm(),
                entity.getHeightCm(),
                entity.getWeightKg(),
                entity.isMerchantOrder() ? entity.getMerchantId() : null,
                entity.getMerchantName(),
                entity.isShopPickup(),
                entity.isReadyForPickup(),
                entity.getReadyAt(),
                entity.getDeliveryAttempts(),
                entity.getLastAttemptReason(),
                DeliveryFailureReason.canRetry(entity.getDeliveryAttempts(), entity.getLastAttemptReason())
        );
    }
}
