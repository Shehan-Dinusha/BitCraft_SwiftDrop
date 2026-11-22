package com.swiftdrop.delivery.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "What the courier needs on the leg they are carrying. Stops and amounts that belong to another "
        + "leg, or to a finished or cancelled delivery, are null.")
public record CourierJobResponse(
        @Schema(description = "Delivery session UUID")
        UUID deliveryId,

        @Schema(description = "True when the order was cancelled: nothing is to be picked up or collected")
        boolean cancelled,

        @Schema(description = "Service level", example = "STANDARD")
        OrderSpeed speed,

        @Schema(description = "Pickup contact and address, until the parcel is picked up")
        CourierJobStop pickup,

        @Schema(description = "Recipient contact and address, for the courier who delivers the parcel")
        CourierJobStop dropOff,

        @Schema(description = "Where to bring the parcel back: the sender, for the courier returning it")
        CourierJobStop returnTo,

        @Schema(description = "The sender's note for the courier", example = "Call when you reach the gate")
        String notes,

        @Schema(description = "What is inside", example = "Books")
        String itemDescription,

        @Schema(description = "Length in cm", example = "30.00")
        BigDecimal lengthCm,

        @Schema(description = "Width in cm", example = "20.00")
        BigDecimal widthCm,

        @Schema(description = "Height in cm", example = "10.00")
        BigDecimal heightCm,

        @Schema(description = "Weight in kg", example = "2.00")
        BigDecimal weightKg,

        @Schema(description = "Delivery fee to collect in cash from the sender, until the parcel is picked up",
                example = "670.00")
        BigDecimal feeToCollect,

        @Schema(description = "ISO currency", example = "LKR")
        String currency
) {
}
