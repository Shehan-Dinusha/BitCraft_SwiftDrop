package com.swiftdrop.contracts.enums;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PII-free status shown to customers and public tracking clients.
 * Internal hub, line-haul, and failure states are never exposed directly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Public parcel status exposed to customers and public tracking clients.", example = "IN_TRANSIT")
public enum PublicOrderStatus {
    CREATED,
    PICKUP_ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    RETURNED_TO_SENDER;

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == RETURNED_TO_SENDER;
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
            case DELIVERED -> DELIVERED;
            case FAILED -> RETURNED_TO_SENDER;
            case CANCELLED -> CANCELLED;
        };
    }
}
