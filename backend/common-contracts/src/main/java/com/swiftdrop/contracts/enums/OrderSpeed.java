package com.swiftdrop.contracts.enums;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Delivery service level selected by the customer or merchant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Delivery service level.", example = "EXPRESS")
public enum OrderSpeed {
    STANDARD,
    EXPRESS,
    SAME_DAY
}
