package com.swiftdrop.dispatch.service;

import java.math.BigDecimal;
import com.swiftdrop.dispatch.domain.enums.VehicleType;
import com.swiftdrop.dispatch.domain.enums.CourierBlock;
import com.swiftdrop.contracts.events.order.OrderUpdatedEvent;
import com.swiftdrop.contracts.enums.OrderSpeed;
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
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
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

import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("Suggestions only offer couriers working in the pickup district")
    void testGetSuggestions_OnlyPickupDistrict() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setPickupDistrict(SriLankanDistrict.COLOMBO);
        CourierShift sameDistrict = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 3);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(SriLankanDistrict.COLOMBO, ShiftStatus.OFFLINE))
                .thenReturn(List.of(sameDistrict));

        var suggestions = dispatchAssignmentService.getSuggestions(orderId);

        assertThat(suggestions).extracting(s -> s.courierId()).containsExactly(sameDistrict.getCourierId());
        assertThat(suggestions.get(0).samePickupDistrict()).isTrue();
        verify(courierShiftRepository).findByCurrentDistrictAndShiftStatusNot(SriLankanDistrict.COLOMBO, ShiftStatus.OFFLINE);
    }

    @Test
    @DisplayName("PICKED_UP records pickup at the event time on an assigned order")
    void testPickedUp_RecordsPickup() {
        DispatchAssignment assignment = assignedAssignment();
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        ParcelStatusChangedEvent event = parcelStatus(PublicOrderStatus.PICKED_UP);

        dispatchAssignmentService.handleParcelStatusChanged(event);

        assertThat(assignment.getPickedUpAt()).isEqualTo(event.timestamp());
        assertThat(assignment.getStatus()).as("pickup is not a lifecycle change").isEqualTo(AssignmentStatus.ASSIGNED);
        verify(assignmentRepository).save(assignment);
        verifyNoInteractions(courierShiftRepository);
    }

    @Test
    @DisplayName("IN_TRANSIT or OUT_FOR_DELIVERY alone record pickup when PICKED_UP was missed")
    void testLaterPostPickupStatusesRecordPickup() {
        for (PublicOrderStatus status : List.of(PublicOrderStatus.IN_TRANSIT, PublicOrderStatus.OUT_FOR_DELIVERY)) {
            DispatchAssignment assignment = assignedAssignment();
            assignment.setStatus(AssignmentStatus.REASSIGNED);
            when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

            dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(status));

            assertThat(assignment.getPickedUpAt()).as(status + " records pickup").isNotNull();
        }
        verify(assignmentRepository, times(2)).save(any(DispatchAssignment.class));
    }

    @Test
    @DisplayName("Duplicate or out-of-order post-pickup events keep the first pickup time and write nothing")
    void testPickupAlreadyRecordedIsNotOverwritten() {
        Instant firstPickup = Instant.parse("2026-11-15T08:30:00Z");
        DispatchAssignment assignment = assignedAssignment();
        assignment.setPickedUpAt(firstPickup);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.PICKED_UP));
        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.IN_TRANSIT));

        assertThat(assignment.getPickedUpAt()).isEqualTo(firstPickup);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A delayed post-pickup event leaves a completed or cancelled assignment untouched")
    void testPickupEventAfterTerminalStateIsIgnored() {
        for (AssignmentStatus terminal : List.of(AssignmentStatus.COMPLETED, AssignmentStatus.CANCELLED)) {
            DispatchAssignment assignment = assignedAssignment();
            assignment.setStatus(terminal);
            when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

            dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.IN_TRANSIT));

            assertThat(assignment.getPickedUpAt()).isNull();
            assertThat(assignment.getStatus()).isEqualTo(terminal);
        }
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A pickup event for an unassigned order is ignored")
    void testPickupEventForUnassignedOrderIsIgnored() {
        DispatchAssignment assignment = assignedAssignment();
        assignment.setCourierId(null);
        assignment.setStatus(AssignmentStatus.UNASSIGNED);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.PICKED_UP));

        assertThat(assignment.getPickedUpAt()).isNull();
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A pickup event for an unknown order is acknowledged without changes")
    void testPickupEventForUnknownOrderIsNoOp() {
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.PICKED_UP));

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("PICKUP_ASSIGNED and CREATED do not record pickup")
    void testPrePickupStatusesAreIgnored() {
        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.PICKUP_ASSIGNED));
        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.CREATED));

        verifyNoInteractions(assignmentRepository, courierShiftRepository);
    }

    // ── Workload release ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Workload never goes below zero when a terminal event releases a courier already at 0")
    void testDecrement_NeverBelowZero() {
        CourierShift courier = courierWithWorkload(0, ShiftStatus.AVAILABLE, 5);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignedAssignment()));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(courier));

        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.DELIVERED));

        assertThat(courier.getActiveParcelsCount()).isZero();
        assertThat(courier.getShiftStatus()).isEqualTo(ShiftStatus.AVAILABLE);
    }

    @Test
    @DisplayName("A terminal event for a reassigned order releases only the new courier's workload")
    void testTerminalEventOnReassignedOrder_ReleasesOnlyNewCourier() {
        UUID previousCourier = UUID.randomUUID();
        DispatchAssignment assignment = assignedAssignment();
        assignment.setStatus(AssignmentStatus.REASSIGNED); // courierId is now the new courier
        CourierShift newCourier = courierWithWorkload(2, ShiftStatus.AVAILABLE, 5);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(courierId)).thenReturn(Optional.of(newCourier));

        dispatchAssignmentService.handleParcelStatusChanged(parcelStatus(PublicOrderStatus.DELIVERED));

        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(newCourier.getActiveParcelsCount()).isEqualTo(1);
        verify(courierShiftRepository, never()).findByCourierId(previousCourier);
        verify(courierShiftRepository, times(1)).save(any(CourierShift.class));
    }

    // ── Suggestions ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Suggestions: an UNASSIGNED order gets suggestions for a new assignment")
    void testSuggestions_UnassignedOrder() {
        CourierShift candidate = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 1);
        stubSuggestionOrder(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);
        when(courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(any(), eq(ShiftStatus.OFFLINE))).thenReturn(List.of(candidate));

        assertThat(dispatchAssignmentService.getSuggestions(orderId))
                .extracting(s -> s.courierId())
                .containsExactly(candidate.getCourierId());
    }

    @Test
    @DisplayName("Suggestions: the courier holding an ASSIGNED or REASSIGNED order is left out")
    void testSuggestions_ExcludeCurrentCourier() {
        for (AssignmentStatus status : List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.REASSIGNED)) {
            UUID current = UUID.randomUUID();
            CourierShift currentCourier = courier(current, SriLankanDistrict.COLOMBO, 0);
            CourierShift other = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 3);
            stubSuggestionOrder(status, current, SriLankanDistrict.COLOMBO);
            when(courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(any(), eq(ShiftStatus.OFFLINE)))
                    .thenReturn(List.of(currentCourier, other));

            assertThat(dispatchAssignmentService.getSuggestions(orderId))
                    .as(status + " order")
                    .extracting(s -> s.courierId())
                    .containsExactly(other.getCourierId());
        }
    }

    @Test
    @DisplayName("Suggestions: a COMPLETED or CANCELLED order is rejected and no couriers are queried")
    void testSuggestions_TerminalOrderRejected() {
        for (AssignmentStatus status : List.of(AssignmentStatus.COMPLETED, AssignmentStatus.CANCELLED)) {
            stubSuggestionOrder(status, UUID.randomUUID(), SriLankanDistrict.COLOMBO);

            assertThatThrownBy(() -> dispatchAssignmentService.getSuggestions(orderId))
                    .isInstanceOf(InvalidAssignmentTransitionException.class)
                    .hasMessageContaining(status.name());
        }
        verify(courierShiftRepository, never()).findByCurrentDistrictAndShiftStatusNot(any(), any());
    }

    @Test
    @DisplayName("Suggestions: an unknown order is reported as not found")
    void testSuggestions_UnknownOrder() {
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatchAssignmentService.getSuggestions(orderId))
                .isInstanceOf(AssignmentNotFoundException.class);
        verifyNoInteractions(courierShiftRepository);
    }

    @Test
    @DisplayName("Suggestions: no eligible courier gives an empty list")
    void testSuggestions_NoEligibleCourier() {
        stubSuggestionOrder(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);
        when(courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(any(), eq(ShiftStatus.OFFLINE))).thenReturn(List.of());

        assertThat(dispatchAssignmentService.getSuggestions(orderId)).isEmpty();
    }

    @Test
    @DisplayName("Suggestions: the only eligible courier being the current one gives an empty list")
    void testSuggestions_OnlyCurrentCourierEligible() {
        UUID current = UUID.randomUUID();
        stubSuggestionOrder(AssignmentStatus.ASSIGNED, current, SriLankanDistrict.COLOMBO);
        when(courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(any(), eq(ShiftStatus.OFFLINE)))
                .thenReturn(List.of(courier(current, SriLankanDistrict.COLOMBO, 0)));

        assertThat(dispatchAssignmentService.getSuggestions(orderId)).isEmpty();
    }

    @Test
    @DisplayName("Suggestions: within the pickup district, lower active workload ranks first")
    void testSuggestions_LowerWorkloadFirstInDistrict() {
        CourierShift busier = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 4);
        CourierShift lighter = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 1);
        CourierShift middle = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 2);
        stubSuggestionOrder(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);
        when(courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(any(), eq(ShiftStatus.OFFLINE)))
                .thenReturn(List.of(busier, lighter, middle));

        assertThat(dispatchAssignmentService.getSuggestions(orderId))
                .extracting(s -> s.courierId())
                .containsExactly(lighter.getCourierId(), middle.getCourierId(), busier.getCourierId());
    }

    @Test
    @DisplayName("Suggestions: equal workload ranks the most recent heartbeat first and no heartbeat last")
    void testSuggestions_HeartbeatOrdering() {
        Instant now = Instant.now();
        CourierShift older = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 1);
        older.setLastHeartbeatAt(now.minusSeconds(600));
        CourierShift recent = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 1);
        recent.setLastHeartbeatAt(now.minusSeconds(10));
        CourierShift never = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 1);
        stubSuggestionOrder(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);
        when(courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(any(), eq(ShiftStatus.OFFLINE)))
                .thenReturn(List.of(never, older, recent));

        assertThat(dispatchAssignmentService.getSuggestions(orderId))
                .extracting(s -> s.courierId())
                .containsExactly(recent.getCourierId(), older.getCourierId(), never.getCourierId());
    }

    @Test
    @DisplayName("Suggestions: with equal district, workload and heartbeat, courier ID decides the order")
    void testSuggestions_CourierIdTieBreak() {
        Instant heartbeat = Instant.now();
        UUID lowId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID highId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        CourierShift high = courier(highId, SriLankanDistrict.COLOMBO, 1);
        high.setLastHeartbeatAt(heartbeat);
        CourierShift low = courier(lowId, SriLankanDistrict.COLOMBO, 1);
        low.setLastHeartbeatAt(heartbeat);
        stubSuggestionOrder(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);

        for (List<CourierShift> input : List.of(List.of(high, low), List.of(low, high))) {
            when(courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(any(), eq(ShiftStatus.OFFLINE))).thenReturn(input);
            assertThat(dispatchAssignmentService.getSuggestions(orderId))
                    .extracting(s -> s.courierId())
                    .containsExactly(lowId, highId);
        }
    }

    @Test
    @DisplayName("Suggestions: the smallest vehicle the parcel fits comes first, and those it does not fit say why")
    void testSuggestions_SmallestFittingVehicleFirst() {
        CourierShift motorcycle = courier(UUID.randomUUID(), VehicleType.MOTORCYCLE, 0);
        CourierShift van = courier(UUID.randomUUID(), VehicleType.VAN, 0);
        CourierShift busyThreeWheeler = courier(UUID.randomUUID(), VehicleType.THREE_WHEELER, 4);
        CourierShift idleThreeWheeler = courier(UUID.randomUUID(), VehicleType.THREE_WHEELER, 1);
        stubSuggestionOrder(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO)
                .setParcel(cm(80), cm(40), cm(30), cm(12));
        when(courierShiftRepository.findByCurrentDistrictAndShiftStatusNot(any(), eq(ShiftStatus.OFFLINE)))
                .thenReturn(List.of(motorcycle, van, busyThreeWheeler, idleThreeWheeler));

        var suggestions = dispatchAssignmentService.getSuggestions(orderId);

        assertThat(suggestions).extracting(s -> s.courierId()).containsExactly(idleThreeWheeler.getCourierId(),
                busyThreeWheeler.getCourierId(), van.getCourierId(), motorcycle.getCourierId());
        assertThat(suggestions).extracting(s -> s.eligible()).containsExactly(true, true, true, false);
        assertThat(suggestions.get(3).ineligibleReason()).isEqualTo(CourierBlock.TOO_LONG);
    }

    @Test
    @DisplayName("Order edited while waiting: size, districts, speed and pickup estimate all follow")
    void testOrderUpdated_Unassigned() {
        DispatchAssignment assignment = stubSuggestionOrder(AssignmentStatus.UNASSIGNED, null, SriLankanDistrict.COLOMBO);

        dispatchAssignmentService.handleOrderUpdated(orderUpdated());

        assertThat(assignment.getPickupDistrict()).isEqualTo(SriLankanDistrict.GALLE);
        assertThat(assignment.getDeliveryDistrict()).isEqualTo(SriLankanDistrict.MATARA);
        assertThat(assignment.getOrderSpeed()).isEqualTo(OrderSpeed.EXPRESS);
        assertThat(assignment.getEstimatedPickupMinutes()).isEqualTo(15);
        assertThat(assignment.getWeightKg()).isEqualByComparingTo("12");
        assertThat(assignment.getVolumeLitres()).isEqualByComparingTo("96");
        verify(assignmentRepository).save(assignment);
    }

    @Test
    @DisplayName("Order edited once a courier is on the way: the size follows, the route stays")
    void testOrderUpdated_Assigned() {
        DispatchAssignment assignment = stubSuggestionOrder(AssignmentStatus.ASSIGNED, courierId, SriLankanDistrict.COLOMBO);

        dispatchAssignmentService.handleOrderUpdated(orderUpdated());

        assertThat(assignment.getPickupDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(assignment.getVolumeLitres()).isEqualByComparingTo("96");
    }

    @Test
    @DisplayName("Order update: ignored for a finished order, retried when the order is not registered yet")
    void testOrderUpdated_FinishedOrUnknown() {
        DispatchAssignment done = stubSuggestionOrder(AssignmentStatus.COMPLETED, courierId, SriLankanDistrict.COLOMBO);
        dispatchAssignmentService.handleOrderUpdated(orderUpdated());
        assertThat(done.getWeightKg()).isNull();
        verify(assignmentRepository, never()).save(any());

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> dispatchAssignmentService.handleOrderUpdated(orderUpdated()))
                .isInstanceOf(AssignmentNotFoundException.class);
    }

    @Test
    @DisplayName("Suggestions: an order without a pickup district ranks every courier by workload alone")
    void testSuggestions_NoPickupDistrict() {
        CourierShift kandy = courier(UUID.randomUUID(), SriLankanDistrict.KANDY, 0);
        CourierShift colombo = courier(UUID.randomUUID(), SriLankanDistrict.COLOMBO, 2);
        stubSuggestionOrder(AssignmentStatus.UNASSIGNED, null, null);
        when(courierShiftRepository.findByShiftStatusNot(ShiftStatus.OFFLINE)).thenReturn(List.of(colombo, kandy));

        var suggestions = dispatchAssignmentService.getSuggestions(orderId);

        assertThat(suggestions).extracting(s -> s.courierId())
                .containsExactly(kandy.getCourierId(), colombo.getCourierId());
        assertThat(suggestions).allSatisfy(s -> assertThat(s.samePickupDistrict()).isFalse());
    }

    private DispatchAssignment stubSuggestionOrder(AssignmentStatus status, UUID currentCourier, SriLankanDistrict pickupDistrict) {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setStatus(status);
        assignment.setCourierId(currentCourier);
        assignment.setPickupDistrict(pickupDistrict);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        return assignment;
    }

    private static BigDecimal cm(int value) {
        return BigDecimal.valueOf(value);
    }

    private OrderUpdatedEvent orderUpdated() {
        return new OrderUpdatedEvent(UUID.randomUUID(), UUID.randomUUID(), orderId, "SD-COL-55555",
                SriLankanDistrict.GALLE, SriLankanDistrict.MATARA, OrderSpeed.EXPRESS,
                cm(80), cm(40), cm(30), cm(12), Instant.now());
    }

    private CourierShift courier(UUID id, VehicleType vehicle, int active) {
        CourierShift courier = courier(id, SriLankanDistrict.COLOMBO, active);
        courier.setVehicleType(vehicle);
        return courier;
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
