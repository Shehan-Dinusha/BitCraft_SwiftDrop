package com.swiftdrop.analytics.domain.projection;

import com.swiftdrop.analytics.messaging.TelemetryEvent;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

/** Reconcile facts by business time, independently of delivery order. */
@Component
public class OrderProjectionReducer {
    public OrderProjection reduce(UUID orderId, List<TelemetryEvent> events) {
        var creations = events.stream().map(TelemetryEvent::payload).filter(OrderCreatedEvent.class::isInstance)
                .map(OrderCreatedEvent.class::cast).sorted(Comparator.comparing(OrderCreatedEvent::timestamp)).toList();
        var assignments = events.stream().map(TelemetryEvent::payload).filter(CourierAssignedEvent.class::isInstance)
                .map(CourierAssignedEvent.class::cast).sorted(Comparator.comparing(CourierAssignedEvent::assignedAt)).toList();
        var statuses = new TreeMap<Instant, List<ParcelStatusChangedEvent>>();
        for (var envelope : events) {
            if (envelope.payload() instanceof ParcelStatusChangedEvent status) {
                statuses.computeIfAbsent(time(status.timestamp()), ignored -> new ArrayList<>()).add(status);
            }
        }
        String conflict = null;
        Instant createdAt = creations.isEmpty() ? null : time(creations.getFirst().timestamp());
        SriLankanDistrict pickup = creations.isEmpty() ? null : creations.getFirst().pickupDistrict();
        if (createdAt != null && creations.stream().filter(c -> time(c.timestamp()).equals(createdAt))
                .map(OrderCreatedEvent::pickupDistrict).distinct().count() > 1) {
            pickup = null;
            conflict = "CONFLICTING_CREATION_DISTRICTS";
        }

        PublicOrderStatus current = null;
        Instant statusAt = null;
        SriLankanDistrict district = pickup;
        PublicOrderStatus outcome = null;
        Instant outcomeAt = null;
        SriLankanDistrict outcomeDistrict = null;
        PublicOrderStatus terminal = null;
        for (var entry : statuses.entrySet()) {
            var group = entry.getValue();
            if (group.stream().map(ParcelStatusChangedEvent::newPublicStatus).distinct().count() > 1
                    || group.stream().map(ParcelStatusChangedEvent::district).distinct().count() > 1) {
                conflict = "CONFLICTING_STATUS_AT_SAME_TIME";
                continue;
            }
            var status = group.getFirst(); // Identical business facts; event ID is irrelevant.
            var next = status.newPublicStatus();
            if (terminal != null && next != terminal
                    && !(terminal == PublicOrderStatus.FAILED && next == PublicOrderStatus.RETURNED_TO_SENDER)) {
                conflict = "STATUS_AFTER_TERMINAL_OUTCOME";
                continue;
            }
            current = next;
            statusAt = entry.getKey();
            district = responsibleDistrict(events, entry.getKey(), status.district());
            // Not delivered: the moment it starts going back to the sender, or is lost. A failed attempt alone
            // decides nothing, it may go out again.
            boolean notDelivered = next == PublicOrderStatus.RETURNING;
            if (terminal == null && (next.isTerminal() || notDelivered)) {
                if (!notDelivered) terminal = next;
                if (outcome == null && (next == PublicOrderStatus.DELIVERED || next == PublicOrderStatus.FAILED || notDelivered)) {
                    outcome = notDelivered ? PublicOrderStatus.FAILED : next;
                    outcomeAt = entry.getKey();
                    outcomeDistrict = district;
                }
            }
            if (next == PublicOrderStatus.RETURNED_TO_SENDER) terminal = next;
        }

        var byAssignmentTime = new TreeMap<Instant, Set<UUID>>();
        for (var assignment : assignments) {
            byAssignmentTime.computeIfAbsent(time(assignment.assignedAt()), ignored -> new HashSet<>()).add(assignment.courierId());
        }
        if (byAssignmentTime.values().stream().anyMatch(ids -> ids.size() > 1)) conflict = "CONFLICTING_ASSIGNMENT_AT_SAME_TIME";
        UUID courier = assignments.isEmpty() ? null : assignments.getLast().courierId();
        Instant assignedAt = assignments.isEmpty() ? null : time(assignments.getLast().assignedAt());
        UUID outcomeCourier = null;
        if (outcomeAt != null) {
            var effective = byAssignmentTime.floorEntry(outcomeAt);
            if (effective != null && effective.getValue().size() == 1) outcomeCourier = effective.getValue().iterator().next();
        }
        // Never choose between conflicting couriers by list order or UUID order.
        if (assignedAt != null && byAssignmentTime.get(assignedAt).size() > 1) courier = null;
        // Handed in at a hub: nobody carries it until a courier is assigned there.
        if (assignedAt != null && handedInAt(events).filter(at -> !at.isBefore(assignedAt)).isPresent()) courier = null;
        return new OrderProjection(orderId, createdAt, pickup, current, statusAt, district,
                courier, assignedAt, outcome, outcomeAt, outcomeDistrict, outcomeCourier, conflict);
    }

    /**
     * The district handling the order at a moment: the pickup district, the drop-off district once its hub
     * has accepted the parcel, and the pickup district again once a return reached its hub. Status events
     * always carry the pickup district.
     */
    public static SriLankanDistrict responsibleDistrict(List<TelemetryEvent> events, Instant at, SriLankanDistrict pickup) {
        return statuses(events)
                .filter(status -> status.internalSubStatus() == InternalQueueStatus.INCOMING
                        || status.internalSubStatus() == InternalQueueStatus.RETURN_AT_HUB)
                .filter(status -> status.destinationDistrict() != null && !time(status.timestamp()).isAfter(time(at)))
                .max(Comparator.comparing(ParcelStatusChangedEvent::timestamp))
                .filter(status -> status.internalSubStatus() == InternalQueueStatus.INCOMING)
                .map(ParcelStatusChangedEvent::destinationDistrict).orElse(pickup);
    }

    private static Optional<Instant> handedInAt(List<TelemetryEvent> events) {
        return statuses(events).filter(status -> status.internalSubStatus() == InternalQueueStatus.AT_HUB
                        || status.internalSubStatus() == InternalQueueStatus.HELD_AT_HUB
                        || status.internalSubStatus() == InternalQueueStatus.RETURN_AT_HUB)
                .map(status -> time(status.timestamp())).max(Comparator.naturalOrder());
    }

    private static Stream<ParcelStatusChangedEvent> statuses(List<TelemetryEvent> events) {
        return events.stream().map(TelemetryEvent::payload).filter(ParcelStatusChangedEvent.class::isInstance)
                .map(ParcelStatusChangedEvent.class::cast);
    }

    private static Instant time(Instant instant) {
        // PostgreSQL stores microseconds. Truncate rather than round across a
        // reporting midnight; canonical inbox JSON retains original precision.
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
