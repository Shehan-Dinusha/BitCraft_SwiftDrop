package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Registers newly created orders in the dispatcher's unassigned queue.
 *
 * <p>Allocation is dispatcher-driven: an {@code order.created} event never assigns
 * a courier, even when couriers are available. The order stays UNASSIGNED until a
 * dispatcher confirms a courier through the explicit assignment operation, using
 * the courier suggestions to help decide.</p>
 */
@Service
public class CourierMatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(CourierMatchingEngine.class);

    private final DispatchAssignmentRepository assignmentRepository;

    public CourierMatchingEngine(DispatchAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Creates the UNASSIGNED dispatch record for an order. Redelivered or
     * duplicate events for an order that is already registered are ignored, so one
     * order always has exactly one dispatch record.
     */
    @Transactional
    public DispatchAssignment processOrderCreated(OrderCreatedEvent event) {
        log.info("Registering order {} for dispatcher allocation, pickup district: {}",
                event.orderId(), event.pickupDistrict());

        Optional<DispatchAssignment> existing = assignmentRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            log.info("Ignoring duplicate order.created event for existing assignment: {}", event.orderId());
            return existing.get();
        }

        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setAssignmentId(UUID.randomUUID());
        assignment.setOrderId(event.orderId());
        assignment.setMerchantId(event.merchantId());
        assignment.setTrackingNumber(event.trackingNumber());
        assignment.setPickupDistrict(event.pickupDistrict());
        assignment.setDeliveryDistrict(event.deliveryDistrict());
        assignment.setOrderSpeed(event.speed() != null ? event.speed() : OrderSpeed.STANDARD);
        assignment.setEstimatedPickupMinutes(calculatePickupMinutes(event.speed()));
        assignment.setStatus(AssignmentStatus.UNASSIGNED);
        assignment.setRetryCount(0);
        assignment.setLastOrderCreatedEventId(event.eventId());

        return assignmentRepository.save(assignment);
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
