package com.swiftdrop.delivery.dto;

import com.swiftdrop.delivery.domain.entity.TrackingBreadcrumb;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "GPS tracking telemetry breadcrumb.")
public record TrackingBreadcrumbResponse(
        @Schema(description = "Delivery UUID")
        UUID deliveryId,

        @Schema(description = "Courier UUID")
        UUID courierId,

        @Schema(description = "Latitude")
        BigDecimal latitude,

        @Schema(description = "Longitude")
        BigDecimal longitude,

        @Schema(description = "Bearing degrees")
        BigDecimal bearingDegrees,

        @Schema(description = "Speed km/h")
        BigDecimal speedKmh,

        @Schema(description = "Accuracy meters")
        BigDecimal accuracy,

        @Schema(description = "Recorded timestamp")
        Instant recordedAt
) {
    public static TrackingBreadcrumbResponse fromEntity(TrackingBreadcrumb entity) {
        if (entity == null) return null;
        return new TrackingBreadcrumbResponse(
                entity.getDeliveryId(),
                entity.getCourierId(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getBearingDegrees(),
                entity.getSpeedKmh(),
                entity.getAccuracy(),
                entity.getRecordedAt()
        );
    }
}
