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
    RECIPIENT_UNAVAILABLE,
    WRONG_ADDRESS,
    INCORRECT_ADDRESS,
    REFUSED_BY_CUSTOMER,
    RECIPIENT_REJECTED,
    ACCESS_RESTRICTED,
    PAYMENT_FAILED,
    DAMAGED,
    DAMAGED_IN_TRANSIT,
    WEATHER_DELAY,
    ADVERSE_WEATHER,
    OTHER
}
