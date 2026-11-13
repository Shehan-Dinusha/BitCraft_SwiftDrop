package com.swiftdrop.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "In-app notification item representation")
public record InAppNotificationResponse(

        @Schema(description = "Notification unique ID", example = "101")
        Long id,

        @Schema(description = "Recipient user UUID", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        UUID userId,

        @Schema(description = "Notification title", example = "Parcel Out for Delivery")
        String title,

        @Schema(description = "Notification body content", example = "Parcel SD-1234 is out for delivery with Sunil.")
        String body,

        @Schema(description = "Associated parcel tracking number", example = "SD-1234")
        String trackingNumber,

        @Schema(description = "Whether the notification has been read", example = "false")
        boolean read,

        @Schema(description = "Timestamp when notification was created")
        Instant createdAt
) {}
