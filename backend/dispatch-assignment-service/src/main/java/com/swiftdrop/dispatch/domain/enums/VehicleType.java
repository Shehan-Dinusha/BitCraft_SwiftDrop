package com.swiftdrop.dispatch.domain.enums;

import com.swiftdrop.contracts.parcel.ParcelLimits;

/**
 * Courier vehicles, smallest first (suggestions rely on this order). Couriers use their own vehicle,
 * registered once in their courier profile. Each type has a default number of parcels it carries at once
 * (a dispatcher can override it per courier) and fixed physical limits: total weight, total volume and the
 * longest side of any one parcel.
 */
public enum VehicleType {
    MOTORCYCLE("motorcycle", 5, 25, 100, 50),
    THREE_WHEELER("three-wheeler", 10, 150, 600, 100),
    VAN("van", 25, 600, 4_000, 200),
    LORRY("lorry", 50, 2_000, ParcelLimits.MAX_VOLUME_LITRES, Integer.parseInt(ParcelLimits.MAX_SIDE_CM));

    private final String label;
    private final int defaultCapacity;
    private final int maxWeightKg;
    private final int maxVolumeLitres;
    private final int maxSideCm;

    VehicleType(String label, int defaultCapacity, int maxWeightKg, int maxVolumeLitres, int maxSideCm) {
        this.label = label;
        this.defaultCapacity = defaultCapacity;
        this.maxWeightKg = maxWeightKg;
        this.maxVolumeLitres = maxVolumeLitres;
        this.maxSideCm = maxSideCm;
    }

    /** Lower-case name for messages, for example "three-wheeler". */
    public String label() {
        return label;
    }

    /** Parcels this vehicle type carries at once unless the dispatcher sets another limit. */
    public int defaultCapacity() {
        return defaultCapacity;
    }

    /** Total weight of everything on board, in kg. */
    public int maxWeightKg() {
        return maxWeightKg;
    }

    /** Total volume of everything on board, in litres. */
    public int maxVolumeLitres() {
        return maxVolumeLitres;
    }

    /** Longest side of any one parcel, in cm. */
    public int maxSideCm() {
        return maxSideCm;
    }
}
