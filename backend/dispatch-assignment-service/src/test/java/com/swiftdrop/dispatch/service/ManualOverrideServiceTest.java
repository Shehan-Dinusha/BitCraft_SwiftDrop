package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.dto.ManualAssignRequest;
import com.swiftdrop.dispatch.dto.ReassignCourierRequest;
import com.swiftdrop.dispatch.exception.AssignmentConflictException;
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;
import com.swiftdrop.dispatch.messaging.publisher.DispatchEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManualOverrideServiceTest {

    @Mock
    private DispatchAssignmentRepository assignmentRepository;

    @Mock
    private CourierShiftRepository courierShiftRepository;

    @Mock
    private DispatchEventPublisher eventPublisher;

    @InjectMocks
    private ManualOverrideService manualOverrideService;

    private UUID orderId;
    private UUID oldCourierId;
    private UUID newCourierId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        oldCourierId = UUID.randomUUID();
        newCourierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Assign: confirms a courier for an unassigned order and publishes assignment events")
    void testManualAssign_UnassignedOrderSucceeds() {
        DispatchAssignment assignment = unassignedOrder();
        CourierShift courier = courier(newCourierId, "New Courier", 1);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(courier));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchAssignment result = manualOverrideService.manualAssign(
                new ManualAssignRequest(orderId, newCourierId, "DISPATCHER_ONE", null));

        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(result.getCourierId()).isEqualTo(newCourierId);
        assertThat(result.getAssignedBy()).isEqualTo("DISPATCHER_ONE");
        verify(courierShiftRepository).save(argThat(c -> c.getCourierId().equals(newCourierId) && c.getActiveParcelsCount() == 2));
        verify(eventPublisher).publishCourierAssigned(any(CourierAssignedEvent.class));
        verify(eventPublisher).publishParcelStatusChanged(any());
    }

    @Test
    @DisplayName("Assign: a second dispatcher cannot replace an existing assignment")
    void testManualAssign_AlreadyAssignedOrderIsRejected() {
        DispatchAssignment assignment = assignedOrder(AssignmentStatus.ASSIGNED);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        ManualAssignRequest secondDispatcher = new ManualAssignRequest(orderId, newCourierId, "DISPATCHER_TWO", null);

        assertThatThrownBy(() -> manualOverrideService.manualAssign(secondDispatcher))
                .isInstanceOf(AssignmentConflictException.class)
                .hasMessageContaining("already assigned");

        assertThat(assignment.getCourierId()).isEqualTo(oldCourierId);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    @Test
    @DisplayName("Assign: a reassigned order is also protected from a normal assignment")
    void testManualAssign_ReassignedOrderIsRejected() {
        when(assignmentRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(assignedOrder(AssignmentStatus.REASSIGNED)));

        assertThatThrownBy(() -> manualOverrideService.manualAssign(
                new ManualAssignRequest(orderId, newCourierId, "DISPATCHER_TWO", null)))
                .isInstanceOf(AssignmentConflictException.class);

        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    @Test
    @DisplayName("Reassign: moves the order to a new courier and rebalances both workloads")
    void testReassign_Success() {
        DispatchAssignment assignment = assignedOrder(AssignmentStatus.ASSIGNED);
        CourierShift oldCourier = courier(oldCourierId, "Old Courier", 2);
        CourierShift newCourier = courier(newCourierId, "New Courier", 1);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(newCourier));
        when(courierShiftRepository.findByCourierId(oldCourierId)).thenReturn(Optional.of(oldCourier));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchAssignment result = manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, newCourierId, "DISPATCH_SUPERVISOR", "Customer urgent request"));

        assertThat(result.getCourierId()).isEqualTo(newCourierId);
        assertThat(result.getCourierName()).isEqualTo("New Courier");
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.REASSIGNED);
        assertThat(result.getAssignedBy()).isEqualTo("DISPATCH_SUPERVISOR");
        verify(courierShiftRepository).save(argThat(c -> c.getCourierId().equals(oldCourierId) && c.getActiveParcelsCount() == 1));
        verify(courierShiftRepository).save(argThat(c -> c.getCourierId().equals(newCourierId) && c.getActiveParcelsCount() == 2));
        verify(eventPublisher).publishCourierAssigned(any(CourierAssignedEvent.class));
        verify(eventPublisher).publishParcelStatusChanged(any());
    }

    @Test
    @DisplayName("Reassign: rejected when the assignment changed since the dispatcher viewed it")
    void testReassign_StaleExpectedCourierIsRejected() {
        DispatchAssignment assignment = assignedOrder(AssignmentStatus.ASSIGNED);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        UUID staleCourierId = UUID.randomUUID();
        assertThatThrownBy(() -> manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, staleCourierId, newCourierId, "DISPATCHER_TWO", null)))
                .isInstanceOf(AssignmentConflictException.class)
                .hasMessageContaining("no longer assigned");

        assertThat(assignment.getCourierId()).isEqualTo(oldCourierId);
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    @Test
    @DisplayName("Reassign: rejected for an order that has no courier yet")
    void testReassign_UnassignedOrderIsRejected() {
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(unassignedOrder()));

        assertThatThrownBy(() -> manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, newCourierId, "DISPATCHER_ONE", null)))
                .isInstanceOf(AssignmentConflictException.class)
                .hasMessageContaining("assign operation");

        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    @Test
    @DisplayName("Reassign: rejected without side effects when the new courier is the current courier")
    void testReassign_SameCourierIsRejected() {
        when(assignmentRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(assignedOrder(AssignmentStatus.ASSIGNED)));

        assertThatThrownBy(() -> manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, oldCourierId, "DISPATCHER_ONE", null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    @Test
    @DisplayName("Reassign: rejected for a completed assignment")
    void testReassign_CompletedAssignmentIsRejected() {
        when(assignmentRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(assignedOrder(AssignmentStatus.COMPLETED)));

        assertThatThrownBy(() -> manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, newCourierId, "DISPATCHER_ONE", null)))
                .isInstanceOf(InvalidAssignmentTransitionException.class);

        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    @Test
    @DisplayName("Manual assign throws CourierNotAvailableException when courier is OFFLINE")
    void testManualAssign_CourierOffline() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);

        CourierShift newCourier = new CourierShift();
        newCourier.setCourierId(newCourierId);
        newCourier.setCourierName("Offline Courier");
        newCourier.setShiftStatus(ShiftStatus.OFFLINE);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(newCourier));

        ManualAssignRequest request = new ManualAssignRequest(orderId, newCourierId, "DISPATCH_SUPERVISOR", null);

        assertThatThrownBy(() -> manualOverrideService.manualAssign(request))
                .isInstanceOf(CourierNotAvailableException.class)
                .hasMessageContaining("OFFLINE");
    }

    @Test
    @DisplayName("Manual assign throws AssignmentNotFoundException when order does not exist")
    void testManualAssign_OrderNotFound() {
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        ManualAssignRequest request = new ManualAssignRequest(orderId, newCourierId, "DISPATCH_SUPERVISOR", null);

        assertThatThrownBy(() -> manualOverrideService.manualAssign(request))
                .isInstanceOf(AssignmentNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Manual assign rejects a completed assignment")
    void testManualAssign_CompletedAssignmentRejected() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setStatus(AssignmentStatus.COMPLETED);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        ManualAssignRequest request = new ManualAssignRequest(orderId, newCourierId, "DISPATCH_SUPERVISOR", null);

        assertThatThrownBy(() -> manualOverrideService.manualAssign(request))
                .isInstanceOf(InvalidAssignmentTransitionException.class);

        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    @Test
    @DisplayName("Manual assign rejects a cancelled assignment")
    void testManualAssign_CancelledAssignmentRejected() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setStatus(AssignmentStatus.CANCELLED);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        ManualAssignRequest request = new ManualAssignRequest(orderId, newCourierId, "DISPATCH_SUPERVISOR", null);

        assertThatThrownBy(() -> manualOverrideService.manualAssign(request))
                .isInstanceOf(InvalidAssignmentTransitionException.class);

        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    private DispatchAssignment unassignedOrder() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setStatus(AssignmentStatus.UNASSIGNED);
        assignment.setPickupDistrict(SriLankanDistrict.COLOMBO);
        assignment.setDeliveryDistrict(SriLankanDistrict.GAMPAHA);
        assignment.setOrderSpeed(OrderSpeed.STANDARD);
        assignment.setTrackingNumber("SD-COL-11111");
        return assignment;
    }

    private DispatchAssignment assignedOrder(AssignmentStatus status) {
        DispatchAssignment assignment = unassignedOrder();
        assignment.setCourierId(oldCourierId);
        assignment.setCourierName("Old Courier");
        assignment.setStatus(status);
        return assignment;
    }

    private CourierShift courier(UUID courierId, String name, int activeParcels) {
        CourierShift courier = new CourierShift();
        courier.setCourierId(courierId);
        courier.setCourierName(name);
        courier.setActiveParcelsCount(activeParcels);
        courier.setMaxCapacity(5);
        courier.setShiftStatus(ShiftStatus.AVAILABLE);
        return courier;
    }
}
