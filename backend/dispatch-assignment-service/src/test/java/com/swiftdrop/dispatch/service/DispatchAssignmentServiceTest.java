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
import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("Handle parcel.status.changed (FAILED): cancels assignment and releases workload")
    void testHandleParcelFailed() {
        DispatchAssignment assignment = assignedAssignment();
        CourierShift courier = courierWithWorkload(1, ShiftStatus.BUSY, 2);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(courier));

        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.FAILED));

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(assignment.getFailureReason()).isEqualTo("Parcel delivery failed");
        assertThat(courier.getActiveParcelsCount()).isEqualTo(0);
        verify(courierShiftRepository).save(courier);
    }

    @Test
    @DisplayName("Repeated terminal status does not decrement courier workload twice")
    void testHandleParcelStatusChanged_IsIdempotentAfterTerminalState() {
        DispatchAssignment assignment = assignedAssignment();
        CourierShift courier = courierWithWorkload(1, ShiftStatus.BUSY, 2);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(courier));

        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.DELIVERED));
        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.DELIVERED));

        assertThat(courier.getActiveParcelsCount()).isEqualTo(0);
        verify(courierShiftRepository, times(1)).save(courier);
    }

    @Test
    @DisplayName("Repeated parcel failure does not decrement courier workload twice")
    void testHandleParcelFailed_IsIdempotentAfterTerminalState() {
        DispatchAssignment assignment = assignedAssignment();
        CourierShift courier = courierWithWorkload(1, ShiftStatus.BUSY, 2);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(courier));

        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.FAILED));
        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.FAILED));

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(courier.getActiveParcelsCount()).isEqualTo(0);
        verify(courierShiftRepository, times(1)).save(courier);
    }

    @Test
    @DisplayName("Terminal assignment rejects a different terminal event")
    void testHandleParcelStatusChanged_RejectsInvalidTerminalTransition() {
        DispatchAssignment assignment = assignedAssignment();
        assignment.setStatus(AssignmentStatus.COMPLETED);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> dispatchAssignmentService.handleParcelStatusChanged(
                parcelStatus(PublicOrderStatus.FAILED)))
                .isInstanceOf(InvalidAssignmentTransitionException.class);

        verify(assignmentRepository, never()).save(any(DispatchAssignment.class));
        verifyNoInteractions(courierShiftRepository);
    }

    @Test
    @DisplayName("Unassigned cancellation transitions without releasing courier workload")
    void testHandleOrderCancelled_UnassignedAssignment() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setStatus(AssignmentStatus.UNASSIGNED);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

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
        verifyNoInteractions(courierShiftRepository);
    }

    @Test
    @DisplayName("Suggestions prefer pickup district before lower workload elsewhere")
    void testGetSuggestions_PrefersPickupDistrict() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setPickupDistrict(SriLankanDistrict.COLOMBO);
        CourierShift sameDistrict = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 3);
        CourierShift elsewhere = courier(UUID.randomUUID(), SriLankanDistrict.KANDY, 0);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findAvailableCandidates(ShiftStatus.AVAILABLE))
                .thenReturn(List.of(elsewhere, sameDistrict));

        var suggestions = dispatchAssignmentService.getSuggestions(orderId);

        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(0).courierId()).isEqualTo(sameDistrict.getCourierId());
        assertThat(suggestions.get(0).samePickupDistrict()).isTrue();
        assertThat(suggestions.get(1).courierId()).isEqualTo(elsewhere.getCourierId());
    }

    private DispatchAssignment assignedAssignment() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setCourierId(courierId);
        assignment.setTrackingNumber("SD-COL-55555");
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        return assignment;
    }

    private CourierShift courierWithWorkload(int active, ShiftStatus status, int capacity) {
        CourierShift courier = new CourierShift();
        courier.setCourierId(courierId);
        courier.setActiveParcelsCount(active);
        courier.setShiftStatus(status);
        courier.setMaxCapacity(capacity);
        return courier;
    }

    private CourierShift courier(UUID id, SriLankanDistrict district, int active) {
        CourierShift courier = new CourierShift();
        courier.setCourierId(id);
        courier.setCourierName(id.toString());
        courier.setCurrentDistrict(district);
        courier.setShiftStatus(ShiftStatus.AVAILABLE);
        courier.setActiveParcelsCount(active);
        courier.setMaxCapacity(5);
        return courier;
    }

    private ParcelStatusChangedEvent parcelStatus(PublicOrderStatus status) {
        return new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SD-COL-55555",
                orderId,
                null,
                PublicOrderStatus.OUT_FOR_DELIVERY,
                status,
                null,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.COLOMBO,
                null,
                Instant.now()
        );
    }
}
