package com.swiftdrop.contracts.enums;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lifecycle of a delivery-fee revision requested by the merchant or dispatcher.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Delivery fee revision approval status.", example = "PENDING")
public enum FeeRevisionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED
}
