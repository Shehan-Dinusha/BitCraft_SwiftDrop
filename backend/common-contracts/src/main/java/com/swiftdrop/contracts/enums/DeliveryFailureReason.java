package com.swiftdrop.contracts.enums;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Structured reason recorded when a delivery attempt fails.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Reason a delivery attempt failed.", example = "CUSTOMER_UNAVAILABLE")
public enum DeliveryFailureReason {
    CUSTOMER_UNAVAILABLE,
    WRONG_ADDRESS,
    REFUSED_BY_CUSTOMER,
    PAYMENT_FAILED,
    DAMAGED,
    WEATHER_DELAY,
    OTHER
}
