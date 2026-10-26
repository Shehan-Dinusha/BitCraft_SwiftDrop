package com.swiftdrop.contracts.enums;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Internal queue and hand-off state owned by the delivery execution FSM.
 * These values are used for operations dashboards and must not be exposed to
 * public tracking clients without mapping through {@link PublicOrderStatus}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Internal parcel queue state used by dispatch and delivery operations.", example = "AT_HUB")
public enum InternalQueueStatus {
    CREATED,
    UNASSIGNED,
    PICKUP_ASSIGNED,
    PICKED_UP,
    AT_HUB,
    OUT_FOR_DELIVERY,
    OUTBOUND,
    INCOMING,
    DELIVERED,
    FAILED_RETURN,
    CANCELLED;

    public PublicOrderStatus toPublicStatus() {
        return switch (this) {
            case CREATED, UNASSIGNED -> PublicOrderStatus.CREATED;
            case PICKUP_ASSIGNED -> PublicOrderStatus.PICKUP_ASSIGNED;
            case PICKED_UP -> PublicOrderStatus.PICKED_UP;
            case AT_HUB, OUTBOUND, INCOMING -> PublicOrderStatus.IN_TRANSIT;
            case OUT_FOR_DELIVERY -> PublicOrderStatus.OUT_FOR_DELIVERY;
            case DELIVERED -> PublicOrderStatus.DELIVERED;
            case FAILED_RETURN -> PublicOrderStatus.RETURNED_TO_SENDER;
            case CANCELLED -> PublicOrderStatus.CANCELLED;
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED_RETURN || this == CANCELLED;
    }

    public static InternalQueueStatus fromDeliveryState(DeliveryState state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case CREATED -> CREATED;
            case ASSIGNED -> PICKUP_ASSIGNED;
            case PICKED_UP -> PICKED_UP;
            case IN_TRANSIT -> OUT_FOR_DELIVERY;
            case DELIVERED -> DELIVERED;
            case FAILED -> FAILED_RETURN;
            case CANCELLED -> CANCELLED;
        };
    }
}
