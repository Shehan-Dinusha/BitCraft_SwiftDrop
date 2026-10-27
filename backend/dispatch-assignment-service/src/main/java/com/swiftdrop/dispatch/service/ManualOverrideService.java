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
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
import com.swiftdrop.dispatch.messaging.publisher.DispatchEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

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

    @Transactional
    public DispatchAssignment manualAssign(ManualAssignRequest request) {
        log.info("Dispatcher manual override assignment for order: {} to courier: {}",
                request.orderId(), request.courierId());

        DispatchAssignment assignment = assignmentRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new AssignmentNotFoundException(
                        "Dispatch assignment not found for order: " + request.orderId()));

        CourierShift newCourier = courierShiftRepository.findByCourierId(request.courierId())
                .orElseThrow(() -> new CourierNotAvailableException(
                        "Courier not found with ID: " + request.courierId()));

        if (newCourier.getShiftStatus() == ShiftStatus.OFFLINE) {
            throw new CourierNotAvailableException(
                    "Courier " + newCourier.getCourierName() + " is currently OFFLINE");
        }

        UUID prevCourierId = assignment.getCourierId();
        if (prevCourierId != null && !prevCourierId.equals(request.courierId())) {
            courierShiftRepository.findByCourierId(prevCourierId).ifPresent(prevCourier -> {
                int count = Math.max(0, prevCourier.getActiveParcelsCount() - 1);
                prevCourier.setActiveParcelsCount(count);
                if (prevCourier.getShiftStatus() == ShiftStatus.BUSY && count < prevCourier.getMaxCapacity()) {
                    prevCourier.setShiftStatus(ShiftStatus.AVAILABLE);
                }
                courierShiftRepository.save(prevCourier);
            });
        }

        // Increment count for new courier
        int newCount = newCourier.getActiveParcelsCount() + 1;
        newCourier.setActiveParcelsCount(newCount);
        if (newCount >= newCourier.getMaxCapacity()) {
            newCourier.setShiftStatus(ShiftStatus.BUSY);
        }
        courierShiftRepository.save(newCourier);

        assignment.setCourierId(newCourier.getCourierId());
        assignment.setCourierName(newCourier.getCourierName());
        assignment.setStatus(prevCourierId != null ? AssignmentStatus.REASSIGNED : AssignmentStatus.ASSIGNED);
        assignment.setAssignedBy(request.assignedBy() != null ? request.assignedBy() : "DISPATCHER");
        assignment.setAssignedAt(Instant.now());
        assignment.setFailureReason(null);
        assignment = assignmentRepository.save(assignment);

        CourierAssignedEvent assignedEvent = new CourierAssignedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                assignment.getOrderId(),
                newCourier.getCourierId(),
                newCourier.getCourierName(),
                assignment.getEstimatedPickupMinutes() != null ? assignment.getEstimatedPickupMinutes() : 25,
                Instant.now(),
                Instant.now()
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

        return assignment;
    }
}
