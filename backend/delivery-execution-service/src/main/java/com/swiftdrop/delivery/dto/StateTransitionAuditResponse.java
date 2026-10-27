package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.delivery.domain.entity.StateTransitionAudit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Delivery lifecycle state transition audit record.")
public record StateTransitionAuditResponse(
        @Schema(description = "Delivery UUID")
        UUID deliveryId,

        @Schema(description = "Order UUID")
        UUID orderId,

        @Schema(description = "Source state")
        DeliveryState fromState,

        @Schema(description = "Destination state")
        DeliveryState toState,

        @Schema(description = "Triggered by identity or client")
        String triggeredBy,

        @Schema(description = "Role of actor who performed transition")
        String actorRole,

        @Schema(description = "Transition notes")
        String notes,

        @Schema(description = "GPS latitude")
        BigDecimal latitude,

        @Schema(description = "GPS longitude")
        BigDecimal longitude,

        @Schema(description = "Audit timestamp")
        Instant createdAt
) {
    public static StateTransitionAuditResponse fromEntity(StateTransitionAudit entity) {
        if (entity == null) return null;
        return new StateTransitionAuditResponse(
                entity.getDeliveryId(),
                entity.getOrderId(),
                entity.getFromState(),
                entity.getToState(),
                entity.getTriggeredBy(),
                entity.getActorRole(),
                entity.getNotes(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getCreatedAt()
        );
    }
}
