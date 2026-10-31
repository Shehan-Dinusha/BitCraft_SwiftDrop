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
import com.swiftdrop.dispatch.exception.AssignmentNotFoundException;
import com.swiftdrop.dispatch.exception.CourierNotAvailableException;
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
    @DisplayName("Manual assign override: successfully reassigns order from old courier to new courier")
    void testManualAssign_ReassignSuccess() {
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setOrderId(orderId);
        assignment.setCourierId(oldCourierId);
        assignment.setCourierName("Old Courier");
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setPickupDistrict(SriLankanDistrict.COLOMBO);
        assignment.setDeliveryDistrict(SriLankanDistrict.GAMPAHA);
        assignment.setOrderSpeed(OrderSpeed.STANDARD);
        assignment.setTrackingNumber("SD-COL-11111");

        CourierShift oldCourier = new CourierShift();
        oldCourier.setCourierId(oldCourierId);
        oldCourier.setCourierName("Old Courier");
        oldCourier.setActiveParcelsCount(2);
        oldCourier.setMaxCapacity(5);
        oldCourier.setShiftStatus(ShiftStatus.AVAILABLE);

        CourierShift newCourier = new CourierShift();
        newCourier.setCourierId(newCourierId);
        newCourier.setCourierName("New Courier");
        newCourier.setActiveParcelsCount(1);
        newCourier.setMaxCapacity(5);
        newCourier.setShiftStatus(ShiftStatus.AVAILABLE);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(courierShiftRepository.findByCourierId(newCourierId)).thenReturn(Optional.of(newCourier));
        when(courierShiftRepository.findByCourierId(oldCourierId)).thenReturn(Optional.of(oldCourier));
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        ManualAssignRequest request = new ManualAssignRequest(orderId, newCourierId, "DISPATCH_SUPERVISOR", "Customer urgent request");
        DispatchAssignment result = manualOverrideService.manualAssign(request);

        assertThat(result.getCourierId()).isEqualTo(newCourierId);
        assertThat(result.getCourierName()).isEqualTo("New Courier");
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.REASSIGNED);
        assertThat(result.getAssignedBy()).isEqualTo("DISPATCH_SUPERVISOR");

        // Old courier decremented
        verify(courierShiftRepository).save(argThat(c -> c.getCourierId().equals(oldCourierId) && c.getActiveParcelsCount() == 1));
        // New courier incremented
        verify(courierShiftRepository).save(argThat(c -> c.getCourierId().equals(newCourierId) && c.getActiveParcelsCount() == 2));

        verify(eventPublisher).publishCourierAssigned(any(CourierAssignedEvent.class));
        verify(eventPublisher).publishParcelStatusChanged(any());
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
}
