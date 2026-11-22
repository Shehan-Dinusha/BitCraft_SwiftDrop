package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
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
import com.swiftdrop.dispatch.dto.ReassignCourierRequest;
import com.swiftdrop.dispatch.exception.AssignmentConflictException;
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;
import com.swiftdrop.dispatch.exception.ReassignmentNotAllowedException;
import com.swiftdrop.dispatch.messaging.publisher.DispatchEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @DisplayName("Assign and reassign are refused when the parcel does not fit the courier's vehicle, and change nothing")
    void parcelTooBigForVehicleIsRefused() {
        CourierShift motorcycle = new CourierShift();
        motorcycle.setCourierId(newCourierId);
        motorcycle.setCourierName("New Courier");
        motorcycle.setCurrentDistrict(SriLankanDistrict.KANDY);
        motorcycle.setShiftStatus(ShiftStatus.AVAILABLE);
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setPickupDistrict(SriLankanDistrict.KANDY);
        assignment.setParcel(new java.math.BigDecimal("120"), new java.math.BigDecimal("10"),
                new java.math.BigDecimal("10"), new java.math.BigDecimal("2"));
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(motorcycle));

        assertThatThrownBy(() -> manualOverrideService.manualAssign(
                new ManualAssignRequest(orderId, newCourierId, "DISPATCHER", "test")))
                .isInstanceOf(CourierNotAvailableException.class)
                .hasMessage("The parcel is 120 cm long; a motorcycle takes up to 50 cm");

        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setCourierId(oldCourierId);
        assertThatThrownBy(() -> manualOverrideService.reassign(
                new ReassignCourierRequest(orderId, oldCourierId, newCourierId, "DISPATCHER", "test")))
                .isInstanceOf(CourierNotAvailableException.class);

        assertThat(assignment.getCourierId()).isEqualTo(oldCourierId);
        assertThat(motorcycle.getActiveParcelsCount()).isZero();
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Manual assign: the published events name the order's customer, so delivery can notify them")
    void testManualAssign_PropagatesCustomerId() {
        UUID customerId = UUID.randomUUID();
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setCustomerId(customerId);
        assignment.setStatus(AssignmentStatus.UNASSIGNED);
        assignment.setPickupDistrict(SriLankanDistrict.KANDY);
        assignment.setDeliveryDistrict(SriLankanDistrict.COLOMBO);
        assignment.setOrderSpeed(OrderSpeed.STANDARD);
        assignment.setTrackingNumber("SD-KAN-22222");

        CourierShift newCourier = new CourierShift();
        newCourier.setCourierId(newCourierId);
        newCourier.setCourierName("New Courier");
        newCourier.setCurrentDistrict(SriLankanDistrict.KANDY);
        newCourier.setActiveParcelsCount(0);
        newCourier.setMaxCapacity(5);
        newCourier.setShiftStatus(ShiftStatus.AVAILABLE);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(newCourier));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        manualOverrideService.manualAssign(new ManualAssignRequest(orderId, newCourierId, "dispatcher.kandy", "Nearest courier"));

        ArgumentCaptor<CourierAssignedEvent> assigned = ArgumentCaptor.forClass(CourierAssignedEvent.class);
        verify(eventPublisher).publishCourierAssigned(assigned.capture());
        assertThat(assigned.getValue().customerId()).isEqualTo(customerId);
        assertThat(assigned.getValue().trackingNumber()).isEqualTo("SD-KAN-22222");
        assertThat(assigned.getValue().pickupDistrict()).isEqualTo(SriLankanDistrict.KANDY);
        assertThat(assigned.getValue().deliveryDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);

        ArgumentCaptor<ParcelStatusChangedEvent> status = ArgumentCaptor.forClass(ParcelStatusChangedEvent.class);
        verify(eventPublisher).publishParcelStatusChanged(status.capture());
        assertThat(status.getValue().customerId()).isEqualTo(customerId);
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
        verify(eventPublisher, never()).publishParcelStatusChanged(any());
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

    @Test
    @DisplayName("Assign events: CREATED -> PICKUP_ASSIGNED, the assigned courier and the request correlation ID")
    void testManualAssign_EventContents() {
        UUID correlationId = UUID.randomUUID();
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(unassignedOrder()));
        when(courierShiftRepository.findByCourierId(newCourierId))
                .thenReturn(Optional.of(courier(newCourierId, "New Courier", 0)));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchAssignment result = manualOverrideService.manualAssign(
                new ManualAssignRequest(orderId, newCourierId, "DISPATCHER_ONE", null), correlationId);

        CourierAssignedEvent assigned = capturedCourierAssigned();
        ParcelStatusChangedEvent status = capturedStatusChanged();
        assertThat(assigned.orderId()).isEqualTo(orderId);
        assertThat(assigned.courierId()).isEqualTo(newCourierId);
        assertThat(assigned.courierName()).isEqualTo("New Courier");
        assertThat(assigned.assignedAt()).isEqualTo(result.getAssignedAt());
        assertThat(status.previousPublicStatus()).isEqualTo(PublicOrderStatus.CREATED);
        assertThat(status.newPublicStatus()).isEqualTo(PublicOrderStatus.PICKUP_ASSIGNED);
        assertThat(assigned.correlationId()).isEqualTo(correlationId);
        assertThat(status.correlationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Reassign events: only courier.assigned, naming the new courier, with the request correlation ID")
    void testReassign_EventContents() {
        UUID correlationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        DispatchAssignment assignment = assignedOrder(AssignmentStatus.ASSIGNED);
        assignment.setCustomerId(customerId);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId))
                .thenReturn(Optional.of(courier(newCourierId, "New Courier", 0)));
        when(courierShiftRepository.findByCourierId(oldCourierId))
                .thenReturn(Optional.of(courier(oldCourierId, "Old Courier", 1)));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, newCourierId, "DISPATCH_SUPERVISOR", null), correlationId);

        CourierAssignedEvent assigned = capturedCourierAssigned();
        assertThat(assigned.courierId()).as("event names the new courier").isEqualTo(newCourierId);
        assertThat(assigned.courierName()).isEqualTo("New Courier");
        assertThat(assigned.trackingNumber()).isEqualTo("SD-COL-11111");
        assertThat(assigned.correlationId()).isEqualTo(correlationId);
        assertThat(assigned.customerId()).as("customer kept for delivery notifications (#75)").isEqualTo(customerId);
        assertThat(assigned.pickupDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(assigned.deliveryDistrict()).isEqualTo(SriLankanDistrict.GAMPAHA);
        verify(eventPublisher, never()).publishParcelStatusChanged(any());
    }

    @Test
    @DisplayName("Reassign: a second reassignment also publishes courier.assigned only")
    void testReassign_FromReassignedPublishesCourierAssignedOnly() {
        when(assignmentRepository.findByOrderId(orderId))
                .thenReturn(Optional.of(assignedOrder(AssignmentStatus.REASSIGNED)));
        when(courierShiftRepository.findByCourierId(newCourierId))
                .thenReturn(Optional.of(courier(newCourierId, "New Courier", 0)));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, newCourierId, "DISPATCH_SUPERVISOR", null));

        assertThat(capturedCourierAssigned().courierId()).isEqualTo(newCourierId);
        verify(eventPublisher, never()).publishParcelStatusChanged(any());
    }

    @Test
    @DisplayName("Reassign: without a request correlation ID, courier.assigned carries a generated one")
    void testReassign_GeneratesCorrelationId() {
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignedOrder(AssignmentStatus.ASSIGNED)));
        when(courierShiftRepository.findByCourierId(newCourierId))
                .thenReturn(Optional.of(courier(newCourierId, "New Courier", 0)));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, newCourierId, "DISPATCH_SUPERVISOR", null), null);

        assertThat(capturedCourierAssigned().correlationId()).isNotNull();
    }

    @Test
    @DisplayName("Assign: without a request correlation ID both events share one generated correlation ID")
    void testManualAssign_GeneratesOneSharedCorrelationId() {
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(unassignedOrder()));
        when(courierShiftRepository.findByCourierId(newCourierId))
                .thenReturn(Optional.of(courier(newCourierId, "New Courier", 0)));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        manualOverrideService.manualAssign(new ManualAssignRequest(orderId, newCourierId, "DISPATCHER_ONE", null), null);

        UUID generated = capturedCourierAssigned().correlationId();
        assertThat(generated).isNotNull();
        assertThat(capturedStatusChanged().correlationId()).isEqualTo(generated);
    }

    @Test
    @DisplayName("Reassign: rejected after pickup without changing the assignment, workloads or events")
    void testReassign_AfterPickupIsRejected() {
        DispatchAssignment assignment = assignedOrder(AssignmentStatus.ASSIGNED);
        assignment.setPickedUpAt(java.time.Instant.parse("2026-11-15T08:30:00Z"));
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, newCourierId, "DISPATCH_SUPERVISOR", null), UUID.randomUUID()))
                .isInstanceOf(ReassignmentNotAllowedException.class)
                .hasMessageContaining("picked up");

        assertThat(assignment.getCourierId()).isEqualTo(oldCourierId);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    @Test
    @DisplayName("Reassign: pickup is reported before a stale expected courier")
    void testReassign_PickupTakesPrecedenceOverStaleCourier() {
        DispatchAssignment assignment = assignedOrder(AssignmentStatus.REASSIGNED);
        assignment.setPickedUpAt(java.time.Instant.parse("2026-11-15T08:30:00Z"));
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, UUID.randomUUID(), newCourierId, "DISPATCH_SUPERVISOR", null)))
                .isInstanceOf(ReassignmentNotAllowedException.class);

        verifyNoInteractions(courierShiftRepository, eventPublisher);
    }

    private CourierAssignedEvent capturedCourierAssigned() {
        ArgumentCaptor<CourierAssignedEvent> captor = ArgumentCaptor.forClass(CourierAssignedEvent.class);
        verify(eventPublisher).publishCourierAssigned(captor.capture());
        return captor.getValue();
    }

    private ParcelStatusChangedEvent capturedStatusChanged() {
        ArgumentCaptor<ParcelStatusChangedEvent> captor = ArgumentCaptor.forClass(ParcelStatusChangedEvent.class);
        verify(eventPublisher).publishParcelStatusChanged(captor.capture());
        return captor.getValue();
    }

    // ── Capacity ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Assign: taking the last capacity slot marks the courier BUSY")
    void testManualAssign_LastSlotMarksCourierBusy() {
        CourierShift courier = courier(newCourierId, "New Courier", 4); // capacity 5
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(unassignedOrder()));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(courier));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        manualOverrideService.manualAssign(new ManualAssignRequest(orderId, newCourierId, "DISPATCHER_ONE", null));

        assertThat(courier.getActiveParcelsCount()).isEqualTo(5);
        assertThat(courier.getShiftStatus()).isEqualTo(ShiftStatus.BUSY);
    }

    @Test
    @DisplayName("Assign: a courier at capacity is rejected and nothing changes")
    void testManualAssign_FullCourierRejected() {
        DispatchAssignment assignment = unassignedOrder();
        CourierShift full = courier(newCourierId, "Full Courier", 5); // AVAILABLE, but 5 of 5
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(full));

        assertThatThrownBy(() -> manualOverrideService.manualAssign(
                new ManualAssignRequest(orderId, newCourierId, "DISPATCHER_ONE", null)))
                .isInstanceOf(CourierNotAvailableException.class)
                .hasMessageContaining("reached capacity");

        assertThat(full.getActiveParcelsCount()).isEqualTo(5);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        verify(courierShiftRepository, never()).save(any());
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Reassign: a new courier at capacity is rejected and neither workload changes")
    void testReassign_FullNewCourierRejected() {
        DispatchAssignment assignment = assignedOrder(AssignmentStatus.ASSIGNED);
        CourierShift full = courier(newCourierId, "Full Courier", 5);
        full.setShiftStatus(ShiftStatus.BUSY);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(full));

        assertThatThrownBy(() -> manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, newCourierId, "DISPATCH_SUPERVISOR", null)))
                .isInstanceOf(CourierNotAvailableException.class);

        assertThat(assignment.getCourierId()).isEqualTo(oldCourierId);
        assertThat(full.getActiveParcelsCount()).isEqualTo(5);
        verify(courierShiftRepository, never()).findByCourierId(oldCourierId);
        verify(courierShiftRepository, never()).save(any());
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Reassign: an OFFLINE new courier is rejected and neither workload changes")
    void testReassign_OfflineNewCourierRejected() {
        DispatchAssignment assignment = assignedOrder(AssignmentStatus.ASSIGNED);
        CourierShift offline = courier(newCourierId, "Offline Courier", 0);
        offline.setShiftStatus(ShiftStatus.OFFLINE);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(offline));

        assertThatThrownBy(() -> manualOverrideService.reassign(new ReassignCourierRequest(
                orderId, oldCourierId, newCourierId, "DISPATCH_SUPERVISOR", null)))
                .isInstanceOf(CourierNotAvailableException.class)
                .hasMessageContaining("OFFLINE");

        assertThat(assignment.getCourierId()).isEqualTo(oldCourierId);
        assertThat(offline.getActiveParcelsCount()).isZero();
        verify(courierShiftRepository, never()).findByCourierId(oldCourierId);
        verify(courierShiftRepository, never()).save(any());
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ── District boundaries ─────────────────────────────────────────────────────

    private static CallerContext dispatcherOf(SriLankanDistrict district) {
        return new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null, district);
    }

    @Test
    @DisplayName("A courier from another district cannot be assigned, even by an admin")
    void aCourierMustWorkInThePickupDistrict() {
        CourierShift kandyCourier = courier(newCourierId, "Kandy Courier", 0);
        kandyCourier.setCurrentDistrict(SriLankanDistrict.KANDY);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(unassignedOrder()));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(kandyCourier));
        CallerContext admin = new CallerContext(UUID.randomUUID(), UserRole.SYSTEM_ADMIN, null, null);

        assertThatThrownBy(() -> manualOverrideService.manualAssign(
                new ManualAssignRequest(orderId, newCourierId, "ADMIN", null), null, admin))
                .isInstanceOf(CourierNotAvailableException.class)
                .hasMessageContaining("works in Kandy")
                .hasMessageContaining("picked up in Colombo");
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Only the pickup district's dispatcher can assign or reassign an order")
    void anotherDistrictsDispatcherCannotManageTheOrder() {
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(unassignedOrder()));
        CallerContext galle = dispatcherOf(SriLankanDistrict.GALLE);

        assertThatThrownBy(() -> manualOverrideService.manualAssign(
                new ManualAssignRequest(orderId, newCourierId, "DISPATCHER", null), null, galle))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Colombo dispatcher");

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignedOrder(AssignmentStatus.ASSIGNED)));
        assertThatThrownBy(() -> manualOverrideService.reassign(
                new ReassignCourierRequest(orderId, oldCourierId, newCourierId, "DISPATCHER", null), null, galle))
                .isInstanceOf(ForbiddenException.class);
        verify(courierShiftRepository, never()).findByCourierId(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("The pickup district's dispatcher assigns a courier of their district")
    void thePickupDistrictsDispatcherAssigns() {
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(unassignedOrder()));
        when(courierShiftRepository.findByCourierId(newCourierId))
                .thenReturn(Optional.of(courier(newCourierId, "Colombo Courier", 0)));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchAssignment result = manualOverrideService.manualAssign(
                new ManualAssignRequest(orderId, newCourierId, "DISPATCHER", null), null,
                dispatcherOf(SriLankanDistrict.COLOMBO));

        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
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
        courier.setCurrentDistrict(SriLankanDistrict.COLOMBO); // the pickup district of unassignedOrder()
        return courier;
    }
}
