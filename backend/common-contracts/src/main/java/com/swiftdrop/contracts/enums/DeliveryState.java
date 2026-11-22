package com.swiftdrop.contracts.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents the lifecycle states of a delivery within the SwiftDrop platform.
 * State transitions are enforced by the Delivery Execution Service and
 * broadcast as {@link com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent}.
 */
@Schema(description = "Lifecycle state of a delivery.", example = "IN_TRANSIT")
public enum DeliveryState {
    CREATED,
    ASSIGNED,
    PICKED_UP,
    /** Different districts only: the first courier handed the parcel in at the pickup district's hub. */
    AT_ORIGIN_HUB,
    /**
     * Same district: the courier is on the way to the recipient. Different districts: the parcel is on its
     * way from the pickup district's hub to the drop-off district's hub (marked Outbound by its dispatcher).
     */
    IN_TRANSIT,
    /** Different districts only: the drop-off district's dispatcher accepted it at their hub. */
    AT_DESTINATION_HUB,
    OUT_FOR_DELIVERY,
    DELIVERED,
    /** A delivery attempt failed; the courier still has the parcel and brings it to their district's hub. */
    ATTEMPT_FAILED,
    /** Back at the hub after a failed attempt; its dispatcher tries again or returns it to the sender. */
    HELD_AT_HUB,
    /** Different districts only: on its way back to the sender's hub. */
    RETURN_IN_TRANSIT,
    /** At the sender's hub, waiting for a courier to bring it back to them. */
    RETURN_AT_HUB,
    /** A courier is bringing it back to the sender. */
    OUT_FOR_RETURN,
    /** The sender has it back. */
    RETURNED,
    /** Final: the pickup failed, or the parcel was lost or damaged. Never a delivery that can be tried again. */
    FAILED,
    CANCELLED
}
