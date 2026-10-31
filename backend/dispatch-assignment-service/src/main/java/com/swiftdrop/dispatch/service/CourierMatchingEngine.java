package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.events.dispatch.DispatchFailedEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.messaging.publisher.DispatchEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CourierMatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(CourierMatchingEngine.class);

    private final DispatchAssignmentRepository assignmentRepository;
    private final CourierShiftRepository courierShiftRepository;
    private final DispatchEventPublisher eventPublisher;

    public CourierMatchingEngine(
            DispatchAssignmentRepository assignmentRepository,
            CourierShiftRepository courierShiftRepository,
            DispatchEventPublisher eventPublisher) {
        this.assignmentRepository = assignmentRepository;
        this.courierShiftRepository = courierShiftRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DispatchAssignment processOrderCreated(OrderCreatedEvent event) {
        log.info("Processing dispatch matching for order: {}, pickup district: {}",
                event.orderId(), event.pickupDistrict());

        DispatchAssignment assignment = assignmentRepository.findByOrderId(event.orderId())
                .orElseGet(() -> {
                    DispatchAssignment newAssignment = new DispatchAssignment();
                    newAssignment.setAssignmentId(UUID.randomUUID());
                    newAssignment.setOrderId(event.orderId());
                    newAssignment.setTrackingNumber(event.trackingNumber());
                    newAssignment.setPickupDistrict(event.pickupDistrict());
                    newAssignment.setDeliveryDistrict(event.deliveryDistrict());
                    newAssignment.setOrderSpeed(event.speed() != null ? event.speed() : OrderSpeed.STANDARD);
                    newAssignment.setStatus(AssignmentStatus.UNASSIGNED);
                    newAssignment.setAssignedBy("AUTO_ENGINE");
                    newAssignment.setRetryCount(0);
                    return newAssignment;
                });

        SriLankanDistrict pickupDistrict = event.pickupDistrict();
        List<CourierShift> availableCouriers = courierShiftRepository.findAvailableCandidates(
                pickupDistrict,
                ShiftStatus.AVAILABLE
        );

        if (availableCouriers.isEmpty()) {
            log.warn("No available couriers in district: {} for order: {}", pickupDistrict, event.orderId());
            int newRetryCount = (assignment.getRetryCount() != null ? assignment.getRetryCount() : 0) + 1;
            assignment.setRetryCount(newRetryCount);
            assignment.setStatus(newRetryCount >= 3 ? AssignmentStatus.FAILED : AssignmentStatus.UNASSIGNED);
            assignment.setFailureReason("No available couriers in " + pickupDistrict);
            assignmentRepository.save(assignment);

            DispatchFailedEvent failedEvent = new DispatchFailedEvent(
                    UUID.randomUUID(),
                    event.correlationId() != null ? event.correlationId() : UUID.randomUUID(),
                    event.orderId(),
                    "No available courier in " + pickupDistrict,
                    newRetryCount,
                    newRetryCount < 3,
                    Instant.now()
            );
            eventPublisher.publishDispatchFailed(failedEvent);
            return assignment;
        }

        // Pick top candidate with lowest workload
        CourierShift selectedCourier = availableCouriers.get(0);
        int updatedCount = selectedCourier.getActiveParcelsCount() + 1;
        selectedCourier.setActiveParcelsCount(updatedCount);
        if (updatedCount >= selectedCourier.getMaxCapacity()) {
            selectedCourier.setShiftStatus(ShiftStatus.BUSY);
        }
        courierShiftRepository.save(selectedCourier);

        int estimatedMinutes = calculatePickupMinutes(event.speed());

        assignment.setCourierId(selectedCourier.getCourierId());
        assignment.setCourierName(selectedCourier.getCourierName());
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAssignedBy("AUTO_ENGINE");
        assignment.setAssignedAt(Instant.now());
        assignment.setEstimatedPickupMinutes(estimatedMinutes);
        assignment.setFailureReason(null);
        assignment = assignmentRepository.save(assignment);

        log.info("Assigned courier: {} ({}) to order: {}",
                selectedCourier.getCourierName(), selectedCourier.getCourierId(), event.orderId());

        // Publish CourierAssignedEvent
        CourierAssignedEvent assignedEvent = new CourierAssignedEvent(
                UUID.randomUUID(),
                event.correlationId() != null ? event.correlationId() : UUID.randomUUID(),
                event.orderId(),
                selectedCourier.getCourierId(),
                selectedCourier.getCourierName(),
                estimatedMinutes,
                Instant.now(),
                Instant.now()
        );
        eventPublisher.publishCourierAssigned(assignedEvent);

        // Advance tracking status to PICKUP_ASSIGNED
        ParcelStatusChangedEvent statusEvent = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                event.correlationId() != null ? event.correlationId() : UUID.randomUUID(),
                event.trackingNumber(),
                event.orderId(),
                event.customerId(),
                PublicOrderStatus.CREATED,
                PublicOrderStatus.PICKUP_ASSIGNED,
                InternalQueueStatus.PICKUP_ASSIGNED,
                event.pickupDistrict(),
                event.deliveryDistrict(),
                null,
                Instant.now()
        );
        eventPublisher.publishParcelStatusChanged(statusEvent);

        return assignment;
    }

    private int calculatePickupMinutes(OrderSpeed speed) {
        if (speed == null) return 30;
        return switch (speed) {
            case EXPRESS -> 15;
            case SAME_DAY -> 20;
            case STANDARD -> 35;
        };
    }
}
