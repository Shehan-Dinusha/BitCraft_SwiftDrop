package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.messaging.publisher.DispatchEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourierMatchingEngineTest {

    @Mock
    private DispatchAssignmentRepository assignmentRepository;

    @Mock
    private CourierShiftRepository courierShiftRepository;

    @Mock
    private DispatchEventPublisher eventPublisher;

    @InjectMocks
    private CourierMatchingEngine matchingEngine;

    private UUID orderId;
    private UUID customerId;
    private UUID courierId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        courierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Process order.created: successfully matches available courier in district")
    void testProcessOrderCreated_Success() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderId,
                "SD-COL-12345",
                customerId,
                UUID.randomUUID(),
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.GAMPAHA,
                OrderSpeed.EXPRESS,
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(450),
                "LKR",
                Instant.now()
        );

        CourierShift courier = new CourierShift();
        courier.setCourierId(courierId);
        courier.setCourierName("Sunil Perera");
        courier.setCurrentDistrict(SriLankanDistrict.COLOMBO);
        courier.setShiftStatus(ShiftStatus.AVAILABLE);
        courier.setActiveParcelsCount(0);
        courier.setMaxCapacity(5);

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(courierShiftRepository.findAvailableCandidates(SriLankanDistrict.COLOMBO, ShiftStatus.AVAILABLE))
                .thenReturn(List.of(courier));
        when(assignmentRepository.save(any(DispatchAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DispatchAssignment result = matchingEngine.processOrderCreated(event);

        assertThat(result).isNotNull();
        assertThat(result.getCourierId()).isEqualTo(courierId);
        assertThat(result.getCourierName()).isEqualTo("Sunil Perera");
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(result.getEstimatedPickupMinutes()).isEqualTo(15); // EXPRESS

        verify(courierShiftRepository).save(argThat(c -> c.getActiveParcelsCount() == 1));

        ArgumentCaptor<CourierAssignedEvent> assignedCaptor = ArgumentCaptor.forClass(CourierAssignedEvent.class);
        verify(eventPublisher).publishCourierAssigned(assignedCaptor.capture());
        assertThat(assignedCaptor.getValue().orderId()).isEqualTo(orderId);
        assertThat(assignedCaptor.getValue().courierId()).isEqualTo(courierId);

        ArgumentCaptor<ParcelStatusChangedEvent> statusCaptor = ArgumentCaptor.forClass(ParcelStatusChangedEvent.class);
        verify(eventPublisher).publishParcelStatusChanged(statusCaptor.capture());
        assertThat(statusCaptor.getValue().newPublicStatus()).isEqualTo(PublicOrderStatus.PICKUP_ASSIGNED);
    }

    @Test
    @DisplayName("Process order.created: no couriers available publishes dispatch.failed")
    void testProcessOrderCreated_NoCouriers() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderId,
                "SD-COL-99999",
                customerId,
                UUID.randomUUID(),
                SriLankanDistrict.JAFFNA,
                SriLankanDistrict.COLOMBO,
                OrderSpeed.STANDARD,
                BigDecimal.ZERO,
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(350),
                "LKR",
                Instant.now()
        );

        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(courierShiftRepository.findAvailableCandidates(SriLankanDistrict.JAFFNA, ShiftStatus.AVAILABLE))
                .thenReturn(Collections.emptyList());
        when(assignmentRepository.save(any(DispatchAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DispatchAssignment result = matchingEngine.processOrderCreated(event);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getFailureReason()).contains("No available couriers in JAFFNA");

        verify(eventPublisher).publishDispatchFailed(argThat(e ->
                e.orderId().equals(orderId) && e.attemptCount() == 1 && e.retryable()
        ));
        verify(eventPublisher, never()).publishCourierAssigned(any());
    }
}
