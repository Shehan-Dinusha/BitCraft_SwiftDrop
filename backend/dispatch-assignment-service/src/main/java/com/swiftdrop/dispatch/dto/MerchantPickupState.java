package com.swiftdrop.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Where a shop's parcel is before it is collected. */
@Schema(description = "WAITING (not packed), READY (packed, no courier yet) or ASSIGNED (a courier is coming)")
public enum MerchantPickupState {
    WAITING, READY, ASSIGNED
}
