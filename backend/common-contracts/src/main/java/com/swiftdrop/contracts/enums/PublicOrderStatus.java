package com.swiftdrop.contracts.enums;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PII-free status shown to customers and public tracking clients.
 * Internal hub and line-haul states are mapped to customer-safe values.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Public parcel status exposed to customers and public tracking clients.", example = "IN_TRANSIT")
public enum PublicOrderStatus {
    CREATED,
    PICKUP_ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    FAILED,
    DELIVERED,
    CANCELLED,
    RETURNED_TO_SENDER;

    public boolean isTerminal() {
        return this == FAILED || this == DELIVERED || this == CANCELLED || this == RETURNED_TO_SENDER;
    }

    public static PublicOrderStatus fromDeliveryState(DeliveryState state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case CREATED -> CREATED;
            case ASSIGNED -> PICKUP_ASSIGNED;
            case PICKED_UP -> PICKED_UP;
            case IN_TRANSIT -> IN_TRANSIT;
            case OUT_FOR_DELIVERY -> OUT_FOR_DELIVERY;
            case DELIVERED -> DELIVERED;
            case FAILED -> FAILED;
            case CANCELLED -> CANCELLED;
        };
    }
}
