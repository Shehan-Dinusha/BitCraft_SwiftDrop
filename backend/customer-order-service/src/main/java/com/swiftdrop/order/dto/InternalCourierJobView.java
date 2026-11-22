package com.swiftdrop.order.dto;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Everything a courier needs to carry an order, for service-to-service use (delivery execution). It holds
 * full names, phones and addresses: the delivery service passes on only what the assigned courier needs
 * on their leg.
 */
@Schema(description = "Addresses, contacts, parcel and cash amounts of an order, for the delivery service.")
public record InternalCourierJobView(

        @Schema(description = "Order UUID")
        UUID orderId,

        @Schema(description = "True when the order was cancelled")
        boolean cancelled,

        @Schema(description = "Service level", example = "STANDARD")
        OrderSpeed speed,

        @Schema(description = "Where the parcel is picked up")
        Stop pickup,

        @Schema(description = "Where the parcel is delivered")
        Stop dropOff,

        @Schema(description = "The sender's note for the courier")
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

        @Schema(description = "Delivery fee the sender pays at pickup", example = "670.00")
        BigDecimal fee,

        @Schema(description = "ISO currency", example = "LKR")
        String currency
) {

    @Schema(description = "A contact and address")
    public record Stop(
            @Schema(description = "Contact name") String name,
            @Schema(description = "Contact phone") String phone,
            @Schema(description = "Alternate phone") String alternatePhone,
            @Schema(description = "Address line 1") String addressLine1,
            @Schema(description = "Address line 2") String addressLine2,
            @Schema(description = "City") String city,
            @Schema(description = "Postal code") String postalCode,
            @Schema(description = "District") SriLankanDistrict district
    ) {
    }
}
