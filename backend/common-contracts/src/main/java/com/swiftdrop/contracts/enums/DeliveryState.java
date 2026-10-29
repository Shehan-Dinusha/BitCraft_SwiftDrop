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
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    CANCELLED
}
