package com.swiftdrop.dispatch.domain.enums;

/**
 * Courier vehicles. Couriers use their own vehicle, registered once in their courier profile.
 * Each type has a default number of parcels it carries at once; a dispatcher can override it per courier.
 */
public enum VehicleType {
    MOTORCYCLE(5),
    THREE_WHEELER(10),
    VAN(25),
    LORRY(50);

    private final int defaultCapacity;

    VehicleType(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    /** Parcels this vehicle type carries at once unless the dispatcher sets another limit. */
    public int defaultCapacity() {
        return defaultCapacity;
    }
}
