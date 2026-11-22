package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.enums.AssignmentLeg;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCancelledEvent;
import com.swiftdrop.contracts.events.order.OrderUpdatedEvent;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.CourierBlock;
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
import java.util.Map;
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
        return getAssignments(status, null, pageable);
    }

    /** Assignments picked up in {@code pickupDistrict}, or in every district when it is {@code null}. */
    @Transactional(readOnly = true)
    public Page<DispatchAssignmentResponse> getAssignments(AssignmentStatus status, SriLankanDistrict pickupDistrict,
                                                           Pageable pageable) {
        Page<DispatchAssignment> page;
        if (pickupDistrict != null) {
            page = status != null
                    ? assignmentRepository.findHandledByAndStatus(pickupDistrict, status, pageable)
                    : assignmentRepository.findHandledBy(pickupDistrict, pageable);
        } else if (status != null) {
            page = assignmentRepository.findByStatus(status, pageable);
        } else {
            page = assignmentRepository.findAll(pageable);
        }
        return page.map(DispatchAssignmentResponse::fromEntity);
    }

    /** Handed in at {@code district}'s hub and waiting to be marked Outbound. */
    @Transactional(readOnly = true)
    public List<DispatchAssignmentResponse> getReadyToSend(SriLankanDistrict district) {
        return assignmentRepository.findReadyToSend(district).stream()
                .map(DispatchAssignmentResponse::fromEntity)
                .toList();
    }

    /** Open orders delivered into {@code district} from elsewhere: shown to its dispatcher read-only. */
    @Transactional(readOnly = true)
    public List<DispatchAssignmentResponse> getIncoming(SriLankanDistrict district) {
        return assignmentRepository.findIncoming(district).stream()
                .map(DispatchAssignmentResponse::fromEntity)
                .toList();
    }

    /** Failed deliveries {@code district} deals with, and returns coming to its hub. */
    @Transactional(readOnly = true)
    public List<DispatchAssignmentResponse> getReturns(SriLankanDistrict district) {
        return assignmentRepository.findReturns(district).stream()
                .map(DispatchAssignmentResponse::fromEntity)
                .toList();
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
        return getUnassignedParcels(null);
    }

    /** The unassigned queue of one pickup district, or of every district when it is {@code null}. */
    @Transactional(readOnly = true)
    public List<DispatchAssignmentResponse> getUnassignedParcels(SriLankanDistrict pickupDistrict) {
        List<DispatchAssignment> queue = pickupDistrict == null
                ? assignmentRepository.findByStatus(AssignmentStatus.UNASSIGNED)
                : assignmentRepository.findHandledByWithStatus(pickupDistrict, AssignmentStatus.UNASSIGNED);
        return queue.stream()
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

    /**
     * The couriers on shift who could be asked to carry an order: for a new assignment when it is
     * UNASSIGNED, or for a reassignment when it is ASSIGNED or REASSIGNED, in which case the courier
     * currently holding it is left out. Those who can take it come first, best first; the rest carry the
     * reason they cannot. A COMPLETED or CANCELLED order cannot be (re)assigned, so it gets none.
     *
     * <p>Best is the smallest vehicle the parcel fits, so bigger ones stay free for bigger parcels, then the
     * emptiest courier. Without a recorded size the vehicle says nothing, so only the load counts.</p>
     */
    @Transactional(readOnly = true)
    public List<CourierSuggestionResponse> getSuggestions(UUID orderId) {
        DispatchAssignment assignment = assignmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AssignmentNotFoundException("Assignment not found for order: " + orderId));
        if (assignment.getStatus().isTerminal()) {
            throw new InvalidAssignmentTransitionException(
                    "Cannot suggest couriers for an assignment in state " + assignment.getStatus());
        }
        UUID currentCourierId = assignment.getStatus() == AssignmentStatus.UNASSIGNED
                ? null
                : assignment.getCourierId();

        // Only couriers of the district handling the order now can carry it: the pickup district on the
        // first leg, the drop-off district on the last. Orders from before the pickup district was
        // recorded have none, so every available courier is offered for them.
        var district = assignment.getResponsibleDistrict();
        List<CourierShift> candidates = (district != null
                ? courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(district, ShiftStatus.OFFLINE)
                : courierShiftRepository.findByShiftStatusNot(ShiftStatus.OFFLINE)).stream()
                .filter(courier -> !courier.getCourierId().equals(currentCourierId))
                .toList();
        Map<UUID, CourierLoad> loads = CourierLoad.all(assignmentRepository,
                candidates.stream().map(CourierShift::getCourierId).toList());
        boolean sized = assignment.getVolumeLitres() != null;

        record Option(CourierShift courier, CourierLoad load, CourierBlock block) {}
        Comparator<Option> ranking = Comparator.comparing((Option option) -> option.block() != null)
                .thenComparing(option -> sized ? option.courier().getVehicleType().ordinal() : 0)
                .thenComparingDouble(option -> CourierFit.fullness(option.courier(), option.load()))
                .thenComparing(option -> option.courier().getLastHeartbeatAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(option -> option.courier().getCourierId());

        return candidates.stream()
                .map(courier -> {
                    CourierLoad load = loads.getOrDefault(courier.getCourierId(), CourierLoad.NONE);
                    return new Option(courier, load, CourierFit.block(courier, load, assignment).orElse(null));
                })
                .sorted(ranking)
                .map(option -> CourierSuggestionResponse.of(option.courier(), option.load(), option.block(), district))
                .toList();
    }

    /**
     * Takes over an edit made before a courier was assigned. The size and weight always apply. Districts and
     * speed apply only while the order still waits for its first courier; once one is on the way the route is
     * fixed. An update that overtakes its {@code order.created} is retried until the order is registered.
     */
    @Transactional
    public void handleOrderUpdated(OrderUpdatedEvent event) {
        DispatchAssignment assignment = assignmentRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new AssignmentNotFoundException(
                        "Dispatch assignment not found for order: " + event.orderId()));
        if (assignment.getStatus().isTerminal()) {
            return;
        }
        assignment.setParcel(event.lengthCm(), event.widthCm(), event.heightCm(), event.actualWeightKg());
        if (assignment.getStatus() == AssignmentStatus.UNASSIGNED && assignment.getLeg() == AssignmentLeg.FIRST_MILE) {
            if (event.pickupDistrict() != null) assignment.setPickupDistrict(event.pickupDistrict());
            if (event.deliveryDistrict() != null) assignment.setDeliveryDistrict(event.deliveryDistrict());
            if (event.speed() != null) {
                assignment.setOrderSpeed(event.speed());
                assignment.setEstimatedPickupMinutes(CourierMatchingEngine.pickupMinutes(event.speed()));
            }
        }
        assignmentRepository.save(assignment);
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
        if (event.newPublicStatus() == PublicOrderStatus.IN_TRANSIT && event.internalSubStatus() != null
                && handleHubStep(event)) {
            return;
        }
        if (event.newPublicStatus() == PublicOrderStatus.DELIVERY_ATTEMPTED
                || event.newPublicStatus() == PublicOrderStatus.RETURNING) {
            handleReturnStep(event);
            return;
        }
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

            find(event).ifPresent(assignment -> {
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
                } else if (event.newPublicStatus() == PublicOrderStatus.RETURNED_TO_SENDER) {
                    assignment.setFailureReason("Returned to the sender");
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
        Optional<DispatchAssignment> assignmentOpt = find(event);
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

    /**
     * The steps between the legs of a cross-district order. Returns false when the event is not one of
     * them (a same-district courier on the way also reports IN_TRANSIT/OUTBOUND), so normal handling runs.
     * Each step is idempotent: a repeated or late event finds the assignment already moved on.
     */
    private boolean handleHubStep(ParcelStatusChangedEvent event) {
        Optional<DispatchAssignment> found = find(event);
        // INCOMING is also how any parcel, same-district ones too, goes out again after a failed attempt.
        boolean hubStep = found.isPresent() && (found.get().crossesDistricts()
                || event.internalSubStatus() == InternalQueueStatus.INCOMING);
        if (!hubStep || found.get().getStatus().isTerminal()) {
            return hubStep;
        }
        DispatchAssignment assignment = found.get();
        switch (event.internalSubStatus()) {
            case AT_HUB -> {
                // Handed in at the pickup district's hub: the first courier is free again.
                if (assignment.getStatus() == AssignmentStatus.ASSIGNED
                        || assignment.getStatus() == AssignmentStatus.REASSIGNED) {
                    UUID firstCourier = assignment.getCourierId();
                    AssignmentStateMachine.transition(assignment.getStatus(), AssignmentStatus.IN_TRANSFER);
                    assignment.setStatus(AssignmentStatus.IN_TRANSFER);
                    assignment.setFirstMileCourierId(firstCourier);
                    assignment.setCourierId(null);
                    assignment.setCourierName(null);
                    assignment.setHubStage(InternalQueueStatus.AT_HUB);
                    assignmentRepository.save(assignment);
                    if (firstCourier != null) decrementCourierWorkload(firstCourier);
                    log.info("Order {} handed in at the {} hub", assignment.getOrderId(), assignment.getPickupDistrict());
                }
                return true;
            }
            case OUTBOUND -> {
                // Marked Outbound by the pickup district. A same-district courier reports OUTBOUND too,
                // while still holding the order: that is the normal pickup boundary, not a hub step.
                if (assignment.getStatus() != AssignmentStatus.IN_TRANSFER) {
                    return false;
                }
                if (assignment.getHubStage() == InternalQueueStatus.AT_HUB) {
                    assignment.setHubStage(InternalQueueStatus.OUTBOUND);
                    assignmentRepository.save(assignment);
                }
                return true;
            }
            case INCOMING -> {
                // Accepted at the drop-off district's hub, or tried again from there: that district's
                // last-mile order.
                if (assignment.getStatus() == AssignmentStatus.IN_TRANSFER) {
                    AssignmentStateMachine.transition(AssignmentStatus.IN_TRANSFER, AssignmentStatus.UNASSIGNED);
                    assignment.setStatus(AssignmentStatus.UNASSIGNED);
                    assignment.setLeg(AssignmentLeg.LAST_MILE);
                    assignment.setHubStage(InternalQueueStatus.INCOMING);
                    // The last-mile courier collects it later: reassignment is allowed until then.
                    assignment.setPickedUpAt(null);
                    assignment.setRetryCount(0);
                    assignmentRepository.save(assignment);
                    log.info("Order {} accepted at the {} hub", assignment.getOrderId(), assignment.getDeliveryDistrict());
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * A failed delivery on its way back: to the hub with the courier, held there, to the sender's hub, and out
     * to the sender. Repeated and late events find the assignment already moved on and change nothing.
     */
    private void handleReturnStep(ParcelStatusChangedEvent event) {
        DispatchAssignment assignment = find(event).orElse(null);
        if (assignment == null || assignment.getStatus().isTerminal() || event.internalSubStatus() == null) {
            return;
        }
        boolean withCourier = assignment.getStatus() == AssignmentStatus.ASSIGNED
                || assignment.getStatus() == AssignmentStatus.REASSIGNED;
        boolean between = assignment.getStatus() == AssignmentStatus.IN_TRANSFER;
        boolean comingBack = assignment.getHubStage() == InternalQueueStatus.RETURN_TO_HUB;
        switch (event.internalSubStatus()) {
            case RETURN_TO_HUB -> {
                if (!withCourier || comingBack) return;
                assignment.recordFailedAttempt(event.failureReason());
                assignment.setHubStage(InternalQueueStatus.RETURN_TO_HUB);
                if (assignment.getPickedUpAt() == null) {
                    assignment.setPickedUpAt(event.timestamp());
                }
            }
            case HELD_AT_HUB -> {
                if (!withCourier) return;
                if (!comingBack) {
                    assignment.recordFailedAttempt(event.failureReason());
                }
                releaseAtHub(assignment);
                assignment.setHubStage(InternalQueueStatus.HELD_AT_HUB);
            }
            case RETURN_OUTBOUND -> {
                if (!between) return;
                assignment.setLeg(AssignmentLeg.RETURN);
                assignment.setHubStage(InternalQueueStatus.RETURN_OUTBOUND);
            }
            case RETURN_AT_HUB -> {
                if (withCourier && assignment.getLeg() == AssignmentLeg.RETURN && assignment.getPickedUpAt() != null) {
                    // The sender could not take it: back at the hub, for another day.
                    releaseAtHub(assignment);
                } else if (!between) {
                    return;
                }
                AssignmentStateMachine.transition(assignment.getStatus(), AssignmentStatus.UNASSIGNED);
                assignment.setStatus(AssignmentStatus.UNASSIGNED);
                assignment.setLeg(AssignmentLeg.RETURN);
                assignment.setHubStage(InternalQueueStatus.RETURN_AT_HUB);
                assignment.setPickedUpAt(null);
                assignment.setRetryCount(0);
            }
            case OUT_FOR_RETURN -> {
                recordPickup(event);
                return;
            }
            default -> {
                return;
            }
        }
        assignmentRepository.save(assignment);
        log.info("Order {} is now {} after a failed delivery", assignment.getOrderId(), assignment.getHubStage());
    }

    private void releaseAtHub(DispatchAssignment assignment) {
        UUID courier = assignment.getCourierId();
        AssignmentStateMachine.transition(assignment.getStatus(), AssignmentStatus.IN_TRANSFER);
        assignment.setStatus(AssignmentStatus.IN_TRANSFER);
        assignment.setCourierId(null);
        assignment.setCourierName(null);
        if (courier != null) decrementCourierWorkload(courier);
    }

    private Optional<DispatchAssignment> find(ParcelStatusChangedEvent event) {
        return event.orderId() != null
                ? assignmentRepository.findByOrderId(event.orderId())
                : assignmentRepository.findByTrackingNumber(event.trackingNumber());
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
            log.info("Decremented courier {} active parcels count to {}", courierId, count);
        });
    }
}
