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
    FAILED,
    CANCELLED
}
