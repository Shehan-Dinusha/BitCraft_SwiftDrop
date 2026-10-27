package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCancelledEvent;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.DispatchAssignmentResponse;
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DispatchAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(DispatchAssignmentService.class);

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

    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Processing order cancellation in dispatch for order: {}", event.orderId());

        assignmentRepository.findByOrderId(event.orderId()).ifPresent(assignment -> {
            if (assignment.getStatus() == AssignmentStatus.ASSIGNED || assignment.getStatus() == AssignmentStatus.REASSIGNED) {
                if (assignment.getCourierId() != null) {
                    decrementCourierWorkload(assignment.getCourierId());
                }
            }
            assignment.setStatus(AssignmentStatus.CANCELLED);
            assignment.setFailureReason("Order cancelled: " + event.reason());
            assignmentRepository.save(assignment);
        });
    }

    @Transactional
    public void handleParcelStatusChanged(ParcelStatusChangedEvent event) {
        if (event.newPublicStatus() == PublicOrderStatus.DELIVERED
                || event.newPublicStatus() == PublicOrderStatus.RETURNED_TO_SENDER
                || event.newPublicStatus() == PublicOrderStatus.CANCELLED) {

            log.info("Parcel {} reached terminal/delivered status: {}. Releasing courier capacity.",
                    event.trackingNumber(), event.newPublicStatus());

            Optional<DispatchAssignment> assignmentOpt = event.orderId() != null
                    ? assignmentRepository.findByOrderId(event.orderId())
                    : assignmentRepository.findByTrackingNumber(event.trackingNumber());

            assignmentOpt.ifPresent(assignment -> {
                if (assignment.getStatus() == AssignmentStatus.ASSIGNED || assignment.getStatus() == AssignmentStatus.REASSIGNED) {
                    if (event.newPublicStatus() == PublicOrderStatus.DELIVERED) {
                        assignment.setStatus(AssignmentStatus.COMPLETED);
                    } else if (event.newPublicStatus() == PublicOrderStatus.CANCELLED) {
                        assignment.setStatus(AssignmentStatus.CANCELLED);
                    }
                    if (assignment.getCourierId() != null) {
                        decrementCourierWorkload(assignment.getCourierId());
                    }
                    assignmentRepository.save(assignment);
                }
            });
        }
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
