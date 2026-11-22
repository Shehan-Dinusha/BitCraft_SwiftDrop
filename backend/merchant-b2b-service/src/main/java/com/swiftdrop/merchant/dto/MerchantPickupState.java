package com.swiftdrop.merchant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "WAITING (not marked ready), READY (waiting for a courier) or ASSIGNED (a courier is coming)")
public enum MerchantPickupState {
    WAITING, READY, ASSIGNED
}
