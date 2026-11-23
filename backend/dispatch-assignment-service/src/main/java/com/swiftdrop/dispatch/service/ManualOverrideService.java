package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.domain.enums.AssignmentLeg;
import com.swiftdrop.dispatch.security.DistrictScope;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.domain.state.AssignmentStateMachine;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.dto.ReassignCourierRequest;
import com.swiftdrop.dispatch.exception.AssignmentConflictException;
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;
import com.swiftdrop.dispatch.exception.ReassignmentNotAllowedException;
import com.swiftdrop.dispatch.messaging.publisher.DispatchEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Dispatcher-driven courier allocation.
 *
 * <p>Assigning and reassigning are deliberately separate operations. A normal
 * assignment only succeeds while the order is still unassigned, so a second
 * dispatcher can never silently replace a courier that was assigned moments
 * earlier. Optimistic locking covers requests that overlap in time; these state
 * checks cover requests that arrive after the first assignment has committed.</p>
 */
@Service
public class ManualOverrideService {

    private static final Logger log = LoggerFactory.getLogger(ManualOverrideService.class);

    private final DispatchAssignmentRepository assignmentRepository;
    private final CourierShiftRepository courierShiftRepository;
    private final DispatchEventPublisher eventPublisher;

    public ManualOverrideService(
            DispatchAssignmentRepository assignmentRepository,
            CourierShiftRepository courierShiftRepository,
            DispatchEventPublisher eventPublisher) {
        this.assignmentRepository = assignmentRepository;
        this.courierShiftRepository = courierShiftRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Assigns a courier to an order that is still waiting in the unassigned queue.
     *
     * @throws AssignmentConflictException if the order already has a courier
     */
    @Transactional
    public DispatchAssignment manualAssign(ManualAssignRequest request) {
        return manualAssign(request, null);
    }

    /**
     * @param correlationId request correlation ID to carry on the published events;
     *                      a new one is generated when {@code null}
     */
    @Transactional
    public DispatchAssignment manualAssign(ManualAssignRequest request, UUID correlationId) {
        return manualAssign(request, correlationId, null);
    }

    /**
     * @param caller the dispatcher or admin acting; a dispatcher may only assign orders picked up in their
     *               own district. {@code null} skips that check (internal callers and tests).
     */
    @Transactional
    public DispatchAssignment manualAssign(ManualAssignRequest request, UUID correlationId, CallerContext caller) {
        log.info("Dispatcher assignment for order: {} to courier: {}", request.orderId(), request.courierId());

        DispatchAssignment assignment = findActiveAssignment(request.orderId());
        if (caller != null) {
            DistrictScope.requireManage(caller, assignment.getResponsibleDistrict());
        }
        if (assignment.getStatus() == AssignmentStatus.IN_TRANSFER) {
            throw new AssignmentConflictException("Order " + request.orderId()
                    + (assignment.getHubStage() == InternalQueueStatus.HELD_AT_HUB
                    ? " is held after a failed delivery. Try again or return it first."
                    : " is between district hubs. A courier is assigned once the next hub accepts it."));
        }
        if (assignment.getStatus() != AssignmentStatus.UNASSIGNED || assignment.getCourierId() != null) {
            throw new AssignmentConflictException("Order " + request.orderId()
                    + " is already assigned to a courier. Refresh the queue, or use reassignment to change the courier.");
        }
        if (!assignment.isReadyForPickup()) {
            throw new AssignmentConflictException("Order " + request.orderId()
                    + " is not packed yet. The shop says when it is ready for pickup.");
        }
        PublicOrderStatus previousPublicStatus = publicStatusOf(assignment.getStatus());

        CourierShift courier = findAcceptingCourier(request.courierId(), assignment);
        AssignmentStateMachine.transition(assignment.getStatus(), AssignmentStatus.ASSIGNED);
        incrementWorkload(courier);

        assignment = applyCourier(assignment, courier, AssignmentStatus.ASSIGNED, request.assignedBy());
        if (assignment.getLeg() != AssignmentLeg.FIRST_MILE) {
            // The parcel waits at a hub: customers already see it on its way, so only the courier
            // assignment is published (a PICKUP_ASSIGNED status would move it backwards).
            publishCourierAssigned(assignment, courier, eventCorrelationId(correlationId));
        } else {
            publishAssignmentEvents(assignment, courier, previousPublicStatus, correlationId);
        }
        return assignment;
    }

    /**
     * Moves an assigned order to a different courier before pickup. The caller must
     * name the courier it expects to be replacing, so a reassignment based on a
     * stale view is rejected instead of overwriting another dispatcher's decision.
     *
     * <p>Only {@code dispatch.courier.assigned} is published: the public status stays
     * PICKUP_ASSIGNED, so a {@code parcel.status.changed} event would carry no
     * change and could regress a downstream status during the pickup-event race.</p>
     *
     * <p>Pickup is known only once delivery-execution's status event has been
     * processed. A reassignment made between the physical pickup and that event
     * cannot be detected here; closing that window needs coordination with
     * delivery-execution-service.</p>
     *
     * @throws AssignmentConflictException     if the order is unassigned or its courier has changed
     * @throws ReassignmentNotAllowedException if dispatch has recorded the parcel as picked up
     */
    @Transactional
    public DispatchAssignment reassign(ReassignCourierRequest request) {
        return reassign(request, null);
    }

    /**
     * @param correlationId request correlation ID to carry on the published events;
     *                      a new one is generated when {@code null}
     */
    @Transactional
    public DispatchAssignment reassign(ReassignCourierRequest request, UUID correlationId) {
        return reassign(request, correlationId, null);
    }

    /** @param caller as for {@link #manualAssign(ManualAssignRequest, UUID, CallerContext)} */
    @Transactional
    public DispatchAssignment reassign(ReassignCourierRequest request, UUID correlationId, CallerContext caller) {
        log.info("Dispatcher reassignment for order: {} from courier: {} to courier: {}",
                request.orderId(), request.expectedCurrentCourierId(), request.newCourierId());

        DispatchAssignment assignment = findActiveAssignment(request.orderId());
        if (caller != null) {
            DistrictScope.requireManage(caller, assignment.getResponsibleDistrict());
        }
        UUID currentCourierId = assignment.getCourierId();
        if (assignment.getStatus() == AssignmentStatus.UNASSIGNED || currentCourierId == null) {
            throw new AssignmentConflictException("Order " + request.orderId()
                    + " has no assigned courier to replace. Use the assign operation instead.");
        }
        if (assignment.getPickedUpAt() != null) {
            throw new ReassignmentNotAllowedException("Order " + request.orderId() + " was picked up at "
                    + assignment.getPickedUpAt() + "; reassignment is not allowed after pickup.");
        }
        if (!currentCourierId.equals(request.expectedCurrentCourierId())) {
            throw new AssignmentConflictException("Order " + request.orderId()
                    + " is no longer assigned to the expected courier. Refresh and retry.");
        }
        if (currentCourierId.equals(request.newCourierId())) {
            throw new IllegalArgumentException("The new courier must differ from the currently assigned courier");
        }

        CourierShift newCourier = findAcceptingCourier(request.newCourierId(), assignment);
        AssignmentStateMachine.transition(assignment.getStatus(), AssignmentStatus.REASSIGNED);
        courierShiftRepository.findByCourierId(currentCourierId).ifPresent(this::decrementWorkload);
        incrementWorkload(newCourier);

        assignment = applyCourier(assignment, newCourier, AssignmentStatus.REASSIGNED, request.assignedBy());
        log.info("Order {} reassigned from courier {} to courier {}",
                assignment.getOrderId(), currentCourierId, newCourier.getCourierId());
        publishCourierAssigned(assignment, newCourier, eventCorrelationId(correlationId));
        return assignment;
    }

    /**
     * Public status implied by the assignment state before this operation. Dispatch
     * only tracks allocation, so an assigned order is reported as PICKUP_ASSIGNED.
     */
    private PublicOrderStatus publicStatusOf(AssignmentStatus status) {
        return switch (status) {
            case UNASSIGNED -> PublicOrderStatus.CREATED;
            case ASSIGNED, REASSIGNED -> PublicOrderStatus.PICKUP_ASSIGNED;
            case IN_TRANSFER -> PublicOrderStatus.IN_TRANSIT;
            case COMPLETED -> PublicOrderStatus.DELIVERED;
            case CANCELLED -> PublicOrderStatus.CANCELLED;
        };
    }

    private DispatchAssignment findActiveAssignment(UUID orderId) {
        DispatchAssignment assignment = assignmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AssignmentNotFoundException(
                        "Dispatch assignment not found for order: " + orderId));
        if (assignment.getStatus().isTerminal()) {
            throw new InvalidAssignmentTransitionException(
                    "Cannot assign a courier to an assignment in state " + assignment.getStatus());
        }
        return assignment;
    }

    /** The courier, if they work in the right district, are free and their vehicle has room for the parcel. */
    private CourierShift findAcceptingCourier(UUID courierId, DispatchAssignment assignment) {
        CourierShift courier = courierShiftRepository.findByCourierId(courierId)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found with ID: " + courierId));
        requireCourierInPickupDistrict(courier, assignment);
        // The courier row is versioned and every assignment updates it, so two assignments that both saw the
        // same load cannot both commit.
        CourierLoad load = CourierLoad.of(assignmentRepository, courierId);
        CourierFit.block(courier, load, assignment).ifPresent(block -> {
            throw new CourierNotAvailableException(CourierFit.message(block, courier, load, assignment));
        });
        return courier;
    }

    /**
     * A courier works only in their own district: the pickup district for the first leg and a return, the
     * drop-off district for the last.
     */
    private void requireCourierInPickupDistrict(CourierShift courier, DispatchAssignment assignment) {
        var district = assignment.getResponsibleDistrict();
        if (district != null && courier.getCurrentDistrict() != district) {
            throw new CourierNotAvailableException("Courier " + courier.getCourierName() + " works in "
                    + DistrictScope.label(courier.getCurrentDistrict()) + ", but this parcel is "
                    + switch (assignment.getLeg()) {
                        case FIRST_MILE -> "picked up in " + DistrictScope.label(district);
                        case LAST_MILE -> "delivered from the " + DistrictScope.label(district) + " hub";
                        case RETURN -> "returned from the " + DistrictScope.label(district) + " hub";
                    });
        }
    }

    private void incrementWorkload(CourierShift courier) {
        int newCount = courier.getActiveParcelsCount() + 1;
        courier.setActiveParcelsCount(newCount);
        // A courier on their break stays on it; they see the new parcel when they are back.
        if (courier.getShiftStatus() == ShiftStatus.AVAILABLE && newCount >= courier.getMaxCapacity()) {
            courier.setShiftStatus(ShiftStatus.BUSY);
        }
        courierShiftRepository.save(courier);
    }

    private void decrementWorkload(CourierShift courier) {
        int count = Math.max(0, courier.getActiveParcelsCount() - 1);
        courier.setActiveParcelsCount(count);
        if (courier.getShiftStatus() == ShiftStatus.BUSY && count < courier.getMaxCapacity()) {
            courier.setShiftStatus(ShiftStatus.AVAILABLE);
        }
        courierShiftRepository.save(courier);
    }

    private DispatchAssignment applyCourier(DispatchAssignment assignment, CourierShift courier,
                                            AssignmentStatus status, String assignedBy) {
        assignment.setCourierId(courier.getCourierId());
        assignment.setCourierName(courier.getCourierName());
        assignment.setStatus(status);
        assignment.setAssignedBy(assignedBy != null ? assignedBy : "DISPATCHER");
        assignment.setAssignedAt(Instant.now());
        assignment.setFailureReason(null);
        return assignmentRepository.save(assignment);
    }

    /** The caller's correlation ID when supplied, otherwise a newly generated one. */
    private UUID eventCorrelationId(UUID correlationId) {
        return correlationId != null ? correlationId : UUID.randomUUID();
    }

    /**
     * First-time assignment: records courier.assigned and the CREATED to
     * PICKUP_ASSIGNED status change for after-commit publication, sharing one
     * correlation ID.
     */
    private void publishAssignmentEvents(DispatchAssignment assignment, CourierShift courier,
                                         PublicOrderStatus previousPublicStatus, UUID correlationId) {
        UUID eventCorrelationId = eventCorrelationId(correlationId);
        publishCourierAssigned(assignment, courier, eventCorrelationId);

        ParcelStatusChangedEvent statusEvent = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                eventCorrelationId,
                assignment.getTrackingNumber(),
                assignment.getOrderId(),
                assignment.getCustomerId(),
                previousPublicStatus,
                PublicOrderStatus.PICKUP_ASSIGNED,
                InternalQueueStatus.PICKUP_ASSIGNED,
                assignment.getPickupDistrict(),
                assignment.getDeliveryDistrict(),
                null,
                Instant.now()
        );
        eventPublisher.publishParcelStatusChanged(statusEvent);
    }

    private void publishCourierAssigned(DispatchAssignment assignment, CourierShift courier, UUID eventCorrelationId) {
        CourierAssignedEvent assignedEvent = new CourierAssignedEvent(
                UUID.randomUUID(),
                eventCorrelationId,
                assignment.getOrderId(),
                courier.getCourierId(),
                courier.getCourierName(),
                assignment.getEstimatedPickupMinutes() != null ? assignment.getEstimatedPickupMinutes() : 25,
                assignment.getAssignedAt(),
                Instant.now(),
                assignment.getTrackingNumber(),
                assignment.getMerchantId(),
                // From dev (#75): delivery-execution copies these onto its parcel-status
                // events; without them the customer gets no picked-up / delivered / failed
                // notifications and analytics rejects status events without a district.
                assignment.getCustomerId(),
                assignment.getPickupDistrict(),
                assignment.getDeliveryDistrict(),
                assignment.getMerchantName()
        );
        eventPublisher.publishCourierAssigned(assignedEvent);
    }
}
