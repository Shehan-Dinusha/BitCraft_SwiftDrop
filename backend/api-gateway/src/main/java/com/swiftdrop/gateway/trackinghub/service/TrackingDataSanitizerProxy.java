package com.swiftdrop.gateway.trackinghub.service;

import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.LocationUpdatedEvent;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class TrackingDataSanitizerProxy {
    public PublicTrackingViewDto sanitize(DeliveryStateChangedEvent event) {
        String trackingId = publicId(event.deliveryId());
        return new PublicTrackingViewDto(trackingId,
                event.newState() == null ? "UNKNOWN" : event.newState().name(),
                null, null, List.of("STATUS_UPDATED"), timestamp(event.timestamp()));
    }

    public PublicTrackingViewDto sanitize(LocationUpdatedEvent event) {
        return new PublicTrackingViewDto(publicId(event.deliveryId()), "IN_TRANSIT",
                round(event.latitude()), round(event.longitude()), List.of("LOCATION_UPDATED"),
                timestamp(event.timestamp()));
    }

    private String publicId(UUID deliveryId) {
        return deliveryId == null ? "unknown" : deliveryId.toString();
    }

    private double round(double coordinate) {
        return Math.round(coordinate * 10000d) / 10000d;
    }

    private Instant timestamp(Instant timestamp) {
        return timestamp == null ? Instant.now() : timestamp;
    }
}
