package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCancelledEvent;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.domain.state.AssignmentStateMachine;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.dto.CourierSuggestionResponse;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;

@Service
public class DispatchAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(DispatchAssignmentService.class);

    /** Public statuses that mean the courier has already collected the parcel. */
    static final Set<PublicOrderStatus> PICKUP_BOUNDARY_STATUSES = EnumSet.of(
            PublicOrderStatus.PICKED_UP,
            PublicOrderStatus.IN_TRANSIT,
            PublicOrderStatus.OUT_FOR_DELIVERY);

    private final DispatchAssignmentRepository assignmentRepository;
    private final CourierShiftRepository courierShiftRepository;

    public DispatchAssignmentService(
            DispatchAssignmentRepository assignmentRepository,
            CourierShiftRepository courierShiftRepository) {
        this.assignmentRepository = assignmentRepository;
        this.courierShiftRepository = courierShiftRepository;
    }

    @Transactional(readOnly = true)
    public Page<DispatchAssignmentResponse> getAssignments(AssignmentStatus status, Pageable pageable) {
        Page<DispatchAssignment> page;
        if (status != null) {
            page = assignmentRepository.findByStatus(status, pageable);
        } else {
            page = assignmentRepository.findAll(pageable);
        }
        return page.map(DispatchAssignmentResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public DispatchAssignmentResponse getAssignmentById(UUID assignmentId) {
        return assignmentRepository.findByAssignmentId(assignmentId)
                .map(DispatchAssignmentResponse::fromEntity)
                .orElseThrow(() -> new AssignmentNotFoundException("Assignment not found: " + assignmentId));
    }

    @Transactional(readOnly = true)
    public DispatchAssignmentResponse getAssignmentByOrderId(UUID orderId) {
        return assignmentRepository.findByOrderId(orderId)
                .map(DispatchAssignmentResponse::fromEntity)
                .orElseThrow(() -> new AssignmentNotFoundException("Assignment not found for order: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<DispatchAssignmentResponse> getUnassignedParcels() {
        return assignmentRepository.findByStatus(AssignmentStatus.UNASSIGNED).stream()
                .map(DispatchAssignmentResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DispatchAssignmentResponse> getAssignmentsForCourier(UUID courierId, AssignmentStatus status) {
        List<DispatchAssignment> list;
        if (status != null) {
            list = assignmentRepository.findByCourierIdAndStatus(courierId, status);
        } else {
            list = assignmentRepository.findByCourierId(courierId);
        }
        return list.stream().map(DispatchAssignmentResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<CourierSuggestionResponse> getSuggestions(UUID orderId) {
        DispatchAssignment assignment = assignmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AssignmentNotFoundException("Assignment not found for order: " + orderId));

        var candidates = courierShiftRepository.findAvailableCandidates(ShiftStatus.AVAILABLE);
        Comparator<CourierShift> ranking =
                Comparator.comparing((CourierShift courier) ->
                                courier.getCurrentDistrict() != assignment.getPickupDistrict())
                        .thenComparingInt(courier -> courier.getActiveParcelsCount())
                        .thenComparing(courier -> courier.getLastHeartbeatAt(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CourierShift::getCourierId);

        return candidates.stream()
                .sorted(ranking)
                .limit(3)
                .map(courier -> CourierSuggestionResponse.fromEntity(courier, assignment.getPickupDistrict()))
                .toList();
    }

    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Processing order cancellation in dispatch for order: {}", event.orderId());

        assignmentRepository.findByOrderId(event.orderId()).ifPresent(assignment -> {
            if (assignment.getStatus() == AssignmentStatus.CANCELLED) {
                return;
            }
            if (assignment.getStatus().isTerminal()) {
                throw new InvalidAssignmentTransitionException(
                        "Cannot cancel an assignment in state " + assignment.getStatus());
            }
            boolean releasedWorkload = assignment.getStatus() == AssignmentStatus.ASSIGNED
                    || assignment.getStatus() == AssignmentStatus.REASSIGNED;
            AssignmentStateMachine.transition(assignment.getStatus(), AssignmentStatus.CANCELLED);
            assignment.setStatus(AssignmentStatus.CANCELLED);
            assignment.setFailureReason("Order cancelled: " + event.reason());
            assignmentRepository.save(assignment);
            if (releasedWorkload && assignment.getCourierId() != null) {
                decrementCourierWorkload(assignment.getCourierId());
            }
        });
    }

    @Transactional
    public void handleParcelStatusChanged(ParcelStatusChangedEvent event) {
        if (PICKUP_BOUNDARY_STATUSES.contains(event.newPublicStatus())) {
            recordPickup(event);
            return;
        }
        if (event.newPublicStatus() == PublicOrderStatus.FAILED
                || event.newPublicStatus() == PublicOrderStatus.DELIVERED
                || event.newPublicStatus() == PublicOrderStatus.RETURNED_TO_SENDER
                || event.newPublicStatus() == PublicOrderStatus.CANCELLED) {

            log.info("Parcel {} reached terminal/delivered status: {}. Releasing courier capacity.",
                    event.trackingNumber(), event.newPublicStatus());

            Optional<DispatchAssignment> assignmentOpt = event.orderId() != null
                    ? assignmentRepository.findByOrderId(event.orderId())
                    : assignmentRepository.findByTrackingNumber(event.trackingNumber());

            assignmentOpt.ifPresent(assignment -> {
                AssignmentStatus targetStatus = toTerminalAssignmentStatus(event.newPublicStatus());
                if (assignment.getStatus().isTerminal()) {
                    if (assignment.getStatus() == targetStatus) {
                        return;
                    }
                    throw new InvalidAssignmentTransitionException(
                            "Cannot transition terminal assignment from " + assignment.getStatus()
                                    + " to " + targetStatus);
                }
                boolean releasedWorkload = assignment.getStatus() == AssignmentStatus.ASSIGNED
                        || assignment.getStatus() == AssignmentStatus.REASSIGNED;
                AssignmentStateMachine.transition(assignment.getStatus(), targetStatus);
                assignment.setStatus(targetStatus);
                if (event.newPublicStatus() == PublicOrderStatus.FAILED) {
                    assignment.setFailureReason("Parcel delivery failed");
                }
                assignmentRepository.save(assignment);
                if (releasedWorkload && assignment.getCourierId() != null) {
                    decrementCourierWorkload(assignment.getCourierId());
                }
            });
        }
    }

    /**
     * Records that the parcel has passed the pickup boundary, which blocks normal
     * reassignment. Any post-pickup status counts, so a lost or delayed PICKED_UP
     * event is covered by the next IN_TRANSIT or OUT_FOR_DELIVERY event.
     *
     * <p>Idempotent: duplicates and out-of-order events find pickup already
     * recorded and write nothing, so they do not bump the row version. When a
     * value is written it goes through the versioned entity update, so a
     * concurrent reassignment and this update cannot both apply blindly: the
     * later commit fails with an optimistic-lock conflict and is retried (the
     * listener) or rejected with 409 (the HTTP request).</p>
     */
    private void recordPickup(ParcelStatusChangedEvent event) {
        Optional<DispatchAssignment> assignmentOpt = event.orderId() != null
                ? assignmentRepository.findByOrderId(event.orderId())
                : assignmentRepository.findByTrackingNumber(event.trackingNumber());
        if (assignmentOpt.isEmpty()) {
            log.debug("No dispatch assignment for {} event on order {}", event.newPublicStatus(), event.orderId());
            return;
        }

        DispatchAssignment assignment = assignmentOpt.get();
        if (assignment.getPickedUpAt() != null || assignment.getStatus().isTerminal()) {
            return;
        }
        if (assignment.getStatus() == AssignmentStatus.UNASSIGNED) {
            log.warn("Ignoring {} event for order {} that has no assigned courier",
                    event.newPublicStatus(), assignment.getOrderId());
            return;
        }

        assignment.setPickedUpAt(event.timestamp() != null ? event.timestamp() : Instant.now());
        assignmentRepository.save(assignment);
        log.info("Order {} passed pickup ({}); reassignment is now blocked",
                assignment.getOrderId(), event.newPublicStatus());
    }

    private AssignmentStatus toTerminalAssignmentStatus(PublicOrderStatus status) {
        return switch (status) {
            case DELIVERED -> AssignmentStatus.COMPLETED;
            case FAILED, CANCELLED, RETURNED_TO_SENDER -> AssignmentStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Non-terminal status: " + status);
        };
    }

    private void decrementCourierWorkload(UUID courierId) {
        courierShiftRepository.findByCourierId(courierId).ifPresent(courier -> {
            int count = Math.max(0, courier.getActiveParcelsCount() - 1);
            courier.setActiveParcelsCount(count);
            if (courier.getShiftStatus() == ShiftStatus.BUSY && count < courier.getMaxCapacity()) {
                courier.setShiftStatus(ShiftStatus.AVAILABLE);
            }
            courierShiftRepository.save(courier);
            log.info("Decremented courier {} active parcels count to {}", courier.getCourierName(), count);
        });
    }
}
