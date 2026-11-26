package com.swiftdrop.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Who to email about a parcel besides the sender: the recipient, at the address the sender entered. For the
 * notification service only; events never carry an email address, so it asks here when a parcel moves.
 */
@Schema(description = "The recipient's email (null when the sender left it out) and the names shown in their emails.")
public record InternalRecipientContact(

        @Schema(description = "Order UUID")
        UUID orderId,

        @Schema(description = "Tracking number", example = "SD-7K4M-9QX2")
        String trackingNumber,

        @Schema(description = "Recipient's email, or null when the sender left it out", example = "kamal@example.com")
        String recipientEmail,

        @Schema(description = "Recipient's name", example = "Kamal Silva")
        String recipientName,

        @Schema(description = "Sender's name, so the recipient knows who the parcel is from", example = "Nimal Perera")
        String senderName,

        @Schema(description = "The day the parcel is promised, if known", example = "2026-11-28")
        LocalDate deliverBy
) {
}
