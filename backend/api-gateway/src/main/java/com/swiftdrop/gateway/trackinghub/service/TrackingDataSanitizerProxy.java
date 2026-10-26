package com.swiftdrop.gateway.trackinghub.service;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingTimelineEntry;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Maps internal delivery events to a strictly PII-free public tracking view.
 * This is the only gateway component allowed to construct public tracking
 * payloads, which keeps the whitelist easy to audit.
 */
@Component
public class TrackingDataSanitizerProxy {

    public PublicTrackingViewDto fromStatus(ParcelStatusChangedEvent event) {
        PublicOrderStatus status = resolveStatus(event);
        PublicTrackingTimelineEntry entry = new PublicTrackingTimelineEntry(
                status,
                event.district(),
                timestamp(event.timestamp())
        );
        return new PublicTrackingViewDto(
                event.trackingNumber(),
                status,
                event.district(),
                event.destinationDistrict(),
                List.of(entry)
        );
    }

    public PublicTrackingViewDto append(PublicTrackingViewDto current, ParcelStatusChangedEvent event) {
        if (current == null) {
            return fromStatus(event);
        }

        PublicOrderStatus status = resolveStatus(event);
        List<PublicTrackingTimelineEntry> timeline = new ArrayList<>(current.statusTimeline());
        timeline.add(new PublicTrackingTimelineEntry(
                status,
                event.district(),
                timestamp(event.timestamp())
        ));

        return new PublicTrackingViewDto(
                current.trackingNumber(),
                status,
                firstNonNull(current.originDistrict(), event.district()),
                firstNonNull(current.destinationDistrict(), event.destinationDistrict()),
                List.copyOf(timeline)
        );
    }

    public PublicTrackingViewDto fromLegacy(DeliveryStateChangedEvent event) {
        PublicOrderStatus status = PublicOrderStatus.fromDeliveryState(event.newState());
        if (status == null) {
            status = PublicOrderStatus.CREATED;
        }
        String trackingNumber = publicId(event.deliveryId());
        PublicTrackingTimelineEntry entry = new PublicTrackingTimelineEntry(
                status,
                null,
                timestamp(event.timestamp())
        );
        return new PublicTrackingViewDto(
                trackingNumber,
                status,
                null,
                null,
                List.of(entry)
        );
    }

    public PublicTrackingViewDto appendLegacy(PublicTrackingViewDto current, DeliveryStateChangedEvent event) {
        if (current == null) {
            return fromLegacy(event);
        }

        PublicOrderStatus status = PublicOrderStatus.fromDeliveryState(event.newState());
        if (status == null) {
            return current;
        }

        List<PublicTrackingTimelineEntry> timeline = new ArrayList<>(current.statusTimeline());
        timeline.add(new PublicTrackingTimelineEntry(status, null, timestamp(event.timestamp())));
        return new PublicTrackingViewDto(
                current.trackingNumber(),
                status,
                current.originDistrict(),
                current.destinationDistrict(),
                List.copyOf(timeline)
        );
    }

    private PublicOrderStatus resolveStatus(ParcelStatusChangedEvent event) {
        if (event.newPublicStatus() != null) {
            return event.newPublicStatus();
        }
        if (event.internalSubStatus() != null) {
            return event.internalSubStatus().toPublicStatus();
        }
        return PublicOrderStatus.CREATED;
    }

    private String publicId(UUID deliveryId) {
        return deliveryId == null ? "unknown" : deliveryId.toString();
    }

    private SriLankanDistrict firstNonNull(SriLankanDistrict first, SriLankanDistrict second) {
        return first != null ? first : second;
    }

    private Instant timestamp(Instant timestamp) {
        return timestamp == null ? Instant.now() : timestamp;
    }
}
