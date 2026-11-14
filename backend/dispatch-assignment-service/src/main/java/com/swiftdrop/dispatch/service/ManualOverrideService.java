package com.swiftdrop.dispatch.service;

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
        log.info("Dispatcher assignment for order: {} to courier: {}", request.orderId(), request.courierId());

        DispatchAssignment assignment = findActiveAssignment(request.orderId());
        if (assignment.getStatus() != AssignmentStatus.UNASSIGNED || assignment.getCourierId() != null) {
            throw new AssignmentConflictException("Order " + request.orderId()
                    + " is already assigned to a courier. Refresh the queue, or use reassignment to change the courier.");
        }

        CourierShift courier = findAcceptingCourier(request.courierId());
        AssignmentStateMachine.transition(assignment.getStatus(), AssignmentStatus.ASSIGNED);
        incrementWorkload(courier);

        assignment = applyCourier(assignment, courier, AssignmentStatus.ASSIGNED, request.assignedBy());
        publishAssignmentEvents(assignment, courier);
        return assignment;
    }

    /**
     * Moves an assigned order to a different courier. The caller must name the
     * courier it expects to be replacing, so a reassignment based on a stale view
     * is rejected instead of overwriting another dispatcher's decision.
     *
     * @throws AssignmentConflictException if the order is unassigned or its courier has changed
     */
    @Transactional
    public DispatchAssignment reassign(ReassignCourierRequest request) {
        log.info("Dispatcher reassignment for order: {} from courier: {} to courier: {}",
                request.orderId(), request.expectedCurrentCourierId(), request.newCourierId());

        DispatchAssignment assignment = findActiveAssignment(request.orderId());
        UUID currentCourierId = assignment.getCourierId();
        if (assignment.getStatus() == AssignmentStatus.UNASSIGNED || currentCourierId == null) {
            throw new AssignmentConflictException("Order " + request.orderId()
                    + " has no assigned courier to replace. Use the assign operation instead.");
        }
        if (!currentCourierId.equals(request.expectedCurrentCourierId())) {
            throw new AssignmentConflictException("Order " + request.orderId()
                    + " is no longer assigned to the expected courier. Refresh and retry.");
        }
        if (currentCourierId.equals(request.newCourierId())) {
            throw new IllegalArgumentException("The new courier must differ from the currently assigned courier");
        }

        CourierShift newCourier = findAcceptingCourier(request.newCourierId());
        AssignmentStateMachine.transition(assignment.getStatus(), AssignmentStatus.REASSIGNED);
        courierShiftRepository.findByCourierId(currentCourierId).ifPresent(this::decrementWorkload);
        incrementWorkload(newCourier);

        assignment = applyCourier(assignment, newCourier, AssignmentStatus.REASSIGNED, request.assignedBy());
        publishAssignmentEvents(assignment, newCourier);
        return assignment;
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

    private CourierShift findAcceptingCourier(UUID courierId) {
        CourierShift courier = courierShiftRepository.findByCourierId(courierId)
                .orElseThrow(() -> new CourierNotAvailableException("Courier not found with ID: " + courierId));
        if (courier.getShiftStatus() == ShiftStatus.OFFLINE) {
            throw new CourierNotAvailableException(
                    "Courier " + courier.getCourierName() + " is currently OFFLINE");
        }
        if (!courier.canAcceptParcel()) {
            throw new CourierNotAvailableException(
                    "Courier " + courier.getCourierName() + " is not available or has reached capacity");
        }
        return courier;
    }

    private void incrementWorkload(CourierShift courier) {
        int newCount = courier.getActiveParcelsCount() + 1;
        courier.setActiveParcelsCount(newCount);
        if (newCount >= courier.getMaxCapacity()) {
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

    private void publishAssignmentEvents(DispatchAssignment assignment, CourierShift courier) {
        CourierAssignedEvent assignedEvent = new CourierAssignedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                assignment.getOrderId(),
                courier.getCourierId(),
                courier.getCourierName(),
                assignment.getEstimatedPickupMinutes() != null ? assignment.getEstimatedPickupMinutes() : 25,
                Instant.now(),
                Instant.now(),
                assignment.getTrackingNumber(),
                assignment.getMerchantId()
        );
        eventPublisher.publishCourierAssigned(assignedEvent);

        ParcelStatusChangedEvent statusEvent = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                assignment.getTrackingNumber(),
                assignment.getOrderId(),
                null,
                PublicOrderStatus.CREATED,
                PublicOrderStatus.PICKUP_ASSIGNED,
                InternalQueueStatus.PICKUP_ASSIGNED,
                assignment.getPickupDistrict(),
                assignment.getDeliveryDistrict(),
                null,
                Instant.now()
        );
        eventPublisher.publishParcelStatusChanged(statusEvent);
    }
}
