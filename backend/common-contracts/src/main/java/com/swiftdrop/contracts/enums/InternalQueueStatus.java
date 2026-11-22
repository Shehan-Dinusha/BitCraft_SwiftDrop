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
    /** The courier has a parcel whose delivery failed and brings it to the hub. */
    RETURN_TO_HUB,
    /** At the hub after a failed attempt, waiting for the dispatcher's decision. */
    HELD_AT_HUB,
    /** On its way back to the sender's hub. */
    RETURN_OUTBOUND,
    /** At the sender's hub, waiting for a courier. */
    RETURN_AT_HUB,
    /** A courier is bringing it back to the sender. */
    OUT_FOR_RETURN,
    FAILED_ATTEMPT,
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
            case RETURN_TO_HUB, HELD_AT_HUB -> PublicOrderStatus.DELIVERY_ATTEMPTED;
            case RETURN_OUTBOUND, RETURN_AT_HUB, OUT_FOR_RETURN -> PublicOrderStatus.RETURNING;
            case FAILED_ATTEMPT -> PublicOrderStatus.FAILED;
            case FAILED_RETURN -> PublicOrderStatus.RETURNED_TO_SENDER;
            case CANCELLED -> PublicOrderStatus.CANCELLED;
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED_ATTEMPT || this == FAILED_RETURN || this == CANCELLED;
    }

    public static InternalQueueStatus fromDeliveryState(DeliveryState state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case CREATED -> CREATED;
            case ASSIGNED -> PICKUP_ASSIGNED;
            case PICKED_UP -> PICKED_UP;
            case AT_ORIGIN_HUB -> AT_HUB;
            case IN_TRANSIT -> OUTBOUND;
            // At the drop-off district's hub, waiting for a last-mile courier.
            case AT_DESTINATION_HUB -> INCOMING;
            case OUT_FOR_DELIVERY -> OUT_FOR_DELIVERY;
            case DELIVERED -> DELIVERED;
            case ATTEMPT_FAILED -> RETURN_TO_HUB;
            case HELD_AT_HUB -> HELD_AT_HUB;
            case RETURN_IN_TRANSIT -> RETURN_OUTBOUND;
            case RETURN_AT_HUB -> RETURN_AT_HUB;
            case OUT_FOR_RETURN -> OUT_FOR_RETURN;
            case RETURNED -> FAILED_RETURN;
            case FAILED -> FAILED_ATTEMPT;
            case CANCELLED -> CANCELLED;
        };
    }
}
