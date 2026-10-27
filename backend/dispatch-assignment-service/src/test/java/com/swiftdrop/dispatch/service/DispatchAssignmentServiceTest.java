package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCancelledEvent;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchAssignmentServiceTest {

    @Mock
    private DispatchAssignmentRepository assignmentRepository;

    @Mock
    private CourierShiftRepository courierShiftRepository;

    @InjectMocks
    private DispatchAssignmentService dispatchAssignmentService;

    private UUID orderId;
    private UUID courierId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        courierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Handle order.cancelled: cancels assignment and decrements courier workload")
    void testHandleOrderCancelled() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setCourierId(courierId);
        assignment.setStatus(AssignmentStatus.ASSIGNED);

        CourierShift courier = new CourierShift();
        courier.setCourierId(courierId);
        courier.setActiveParcelsCount(2);
        courier.setShiftStatus(ShiftStatus.AVAILABLE);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(courier));

        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                "Customer requested cancellation",
                "CUSTOMER",
                Instant.now()
        );

        dispatchAssignmentService.handleOrderCancelled(event);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        verify(assignmentRepository).save(assignment);
        verify(courierShiftRepository).save(argThat(c -> c.getActiveParcelsCount() == 1));
    }

    @Test
    @DisplayName("Handle parcel.status.changed (DELIVERED): marks assignment completed and decrements courier workload")
    void testHandleParcelDelivered() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setCourierId(courierId);
        assignment.setTrackingNumber("SD-COL-55555");
        assignment.setStatus(AssignmentStatus.ASSIGNED);

        CourierShift courier = new CourierShift();
        courier.setCourierId(courierId);
        courier.setActiveParcelsCount(1);
        courier.setShiftStatus(ShiftStatus.BUSY);
        courier.setMaxCapacity(1);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(courier));

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SD-COL-55555",
                orderId,
                null,
                PublicOrderStatus.OUT_FOR_DELIVERY,
                PublicOrderStatus.DELIVERED,
                null,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.COLOMBO,
                null,
                Instant.now()
        );

        dispatchAssignmentService.handleParcelStatusChanged(event);

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        verify(assignmentRepository).save(assignment);
        verify(courierShiftRepository).save(argThat(c ->
                c.getActiveParcelsCount() == 0 && c.getShiftStatus() == ShiftStatus.AVAILABLE
        ));
    }
}
