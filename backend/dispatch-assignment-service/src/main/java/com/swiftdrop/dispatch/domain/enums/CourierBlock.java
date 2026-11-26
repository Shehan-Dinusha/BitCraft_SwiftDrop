package com.swiftdrop.dispatch.domain.enums;

/** Why a courier cannot take a parcel. */
public enum CourierBlock {
    OFF_SHIFT,
    /** The parcel waits at the hub, and the courier has already left on their run. */
    ON_RUN,
    /** A pickup, and pickups are given out only until 15:00. */
    PICKUPS_CLOSED,
    /** The parcel's longest side does not fit the vehicle. */
    TOO_LONG,
    /** The parcel alone is heavier than the vehicle carries. */
    TOO_HEAVY,
    /** The parcel alone is bigger than the vehicle carries. */
    TOO_BIG,
    /** The courier already carries their number of parcels. */
    PARCEL_LIMIT,
    /** It fits the vehicle, but not on top of the weight already assigned. */
    WEIGHT_LIMIT,
    /** It fits the vehicle, but not beside the volume already assigned. */
    VOLUME_LIMIT
}
