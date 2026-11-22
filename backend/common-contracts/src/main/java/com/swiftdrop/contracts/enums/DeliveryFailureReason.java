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
    LOST,
    WEATHER_DELAY,
    ADVERSE_WEATHER,
    OTHER;

    /** SwiftDrop lost or damaged the parcel, so there is nothing to deliver or return. */
    public boolean isLoss() {
        return this == DAMAGED || this == DAMAGED_IN_TRANSIT || this == LOST;
    }

    /** How often SwiftDrop tries to deliver a parcel before it goes back to the sender. */
    public static final int MAX_ATTEMPTS = 2;

    /** Whether a parcel may go out again, after that many attempts and this reason for the last one. */
    public static boolean canRetry(int attempts, DeliveryFailureReason last) {
        return attempts < MAX_ATTEMPTS && (last == null || !last.isRefusal() && !last.isLoss());
    }

    /** The recipient does not want it, so it is not tried again. */
    public boolean isRefusal() {
        return this == REFUSED_BY_CUSTOMER || this == RECIPIENT_REJECTED;
    }
}
