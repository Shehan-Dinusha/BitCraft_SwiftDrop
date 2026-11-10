package com.swiftdrop.gateway.trackinghub.service;

import com.swiftdrop.contracts.dto.tracking.HistoryEntry;
import com.swiftdrop.contracts.dto.tracking.InternalTrackingView;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingTimelineEntry;
import com.swiftdrop.gateway.trackinghub.dto.PublicTrackingViewDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps internal delivery events and the order-service tracking view to a public
 * tracking payload with no raw PII. Contact details are already masked by the
 * order service; this is the only gateway component allowed to construct public
 * tracking payloads, which keeps the whitelist easy to audit.
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
                null,
                null,
                null,
                null,
                null,
                null,
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
                current.originCity(),
                current.destinationCity(),
                current.senderNameMasked(),
                current.senderPhoneMasked(),
                current.receiverNameMasked(),
                current.receiverPhoneMasked(),
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

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private Instant timestamp(Instant timestamp) {
        return timestamp == null ? Instant.now() : timestamp;
    }

    public PublicTrackingViewDto fromInternalView(InternalTrackingView view) {
        List<HistoryEntry> history = view.history() == null ? List.of() : view.history();
        List<PublicTrackingTimelineEntry> timeline = history.stream()
                .map(h -> new PublicTrackingTimelineEntry(
                        h.status(),
                        null,
                        h.occurredAt()))
                .toList();
        return new PublicTrackingViewDto(
                view.trackingNumber(),
                view.status(),
                view.pickupDistrict(),
                view.deliveryDistrict(),
                view.pickupCity(),
                view.deliveryCity(),
                view.senderNameMasked(),
                view.senderPhoneMasked(),
                view.receiverNameMasked(),
                view.receiverPhoneMasked(),
                timeline);
    }

    /**
     * Adds city and masked contact details from the order service to an existing
     * event-derived view without discarding its richer live timeline.
     */
    public PublicTrackingViewDto mergeContactDetails(PublicTrackingViewDto current, InternalTrackingView internal) {
        if (current == null) {
            return fromInternalView(internal);
        }
        PublicTrackingViewDto fallback = fromInternalView(internal);
        return new PublicTrackingViewDto(
                current.trackingNumber(),
                firstNonNull(current.currentPublicStatus(), fallback.currentPublicStatus()),
                firstNonNull(current.originDistrict(), fallback.originDistrict()),
                firstNonNull(current.destinationDistrict(), fallback.destinationDistrict()),
                firstNonNull(current.originCity(), fallback.originCity()),
                firstNonNull(current.destinationCity(), fallback.destinationCity()),
                firstNonNull(current.senderNameMasked(), fallback.senderNameMasked()),
                firstNonNull(current.senderPhoneMasked(), fallback.senderPhoneMasked()),
                firstNonNull(current.receiverNameMasked(), fallback.receiverNameMasked()),
                firstNonNull(current.receiverPhoneMasked(), fallback.receiverPhoneMasked()),
                current.statusTimeline() == null || current.statusTimeline().isEmpty()
                        ? fallback.statusTimeline()
                        : current.statusTimeline());
    }

    /** True when the cached view has no city or masked contact details yet. */
    public boolean needsContactDetails(PublicTrackingViewDto view) {
        return view != null
                && view.originCity() == null
                && view.destinationCity() == null
                && view.senderNameMasked() == null
                && view.receiverNameMasked() == null;
    }
}
