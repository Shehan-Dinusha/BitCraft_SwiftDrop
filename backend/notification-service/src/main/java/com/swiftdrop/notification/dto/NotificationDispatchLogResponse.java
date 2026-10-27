package com.swiftdrop.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Notification dispatch audit record")
public record NotificationDispatchLogResponse(
        @Schema(description = "Log Record UUID")
        UUID logId,

        @Schema(description = "Recipient UUID")
        UUID recipientId,

        @Schema(description = "Dispatch channel: EMAIL, SMS, or PUSH")
        String channel,

        @Schema(description = "Triggering template identifier", example = "PARCEL_OUT_FOR_DELIVERY")
        String templateCode,

        @Schema(description = "Destination address/phone", example = "+94771234567")
        String destination,

        @Schema(description = "Notification subject line")
        String subject,

        @Schema(description = "Rendered notification message body")
        String content,

        @Schema(description = "Dispatch status: SENT, FAILED, or SUPPRESSED")
        String status,

        @Schema(description = "Failure cause if status is FAILED")
        String failureReason,

        @Schema(description = "Timestamp when dispatched")
        Instant sentAt,

        @Schema(description = "Business reference ID (e.g. tracking number or order UUID)")
        String referenceId,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {
}
