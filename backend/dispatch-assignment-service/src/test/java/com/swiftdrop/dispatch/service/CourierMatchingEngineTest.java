package com.swiftdrop.dispatch.service;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * order.created intake for dispatcher-driven allocation. Courier repositories and
 * the event publisher are deliberately not collaborators any more: intake cannot
 * assign a courier or announce an assignment.
 */
@ExtendWith(MockitoExtension.class)
class CourierMatchingEngineTest {

    @Mock
    private DispatchAssignmentRepository assignmentRepository;

    @InjectMocks
    private CourierMatchingEngine matchingEngine;

    private UUID orderId;
    private UUID merchantId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        customerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("order.created registers an UNASSIGNED order with its order data and no courier")
    void testProcessOrderCreated_RegistersUnassignedOrder() {
        OrderCreatedEvent event = event(UUID.randomUUID(), OrderSpeed.EXPRESS);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchAssignment result = matchingEngine.processOrderCreated(event);

        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        assertThat(result.getCourierId()).isNull();
        assertThat(result.getCourierName()).isNull();
        assertThat(result.getAssignedAt()).isNull();
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        assertThat(result.getCustomerId())
                .as("kept for the dispatcher's later assignment notifications (#75)")
                .isEqualTo(customerId);
        assertThat(result.getTrackingNumber()).isEqualTo("SD-COL-12345");
        assertThat(result.getPickupDistrict()).isEqualTo(SriLankanDistrict.COLOMBO);
        assertThat(result.getDeliveryDistrict()).isEqualTo(SriLankanDistrict.GAMPAHA);
        assertThat(result.getOrderSpeed()).isEqualTo(OrderSpeed.EXPRESS);
        assertThat(result.getEstimatedPickupMinutes()).isEqualTo(15);
        assertThat(result.getRetryCount()).isZero();
        assertThat(result.getLastOrderCreatedEventId()).isEqualTo(event.eventId());
    }

    @Test
    @DisplayName("order.created without a speed defaults to STANDARD")
    void testProcessOrderCreated_DefaultsMissingSpeed() {
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any(DispatchAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchAssignment result = matchingEngine.processOrderCreated(event(UUID.randomUUID(), null));

        assertThat(result.getOrderSpeed()).isEqualTo(OrderSpeed.STANDARD);
        assertThat(result.getEstimatedPickupMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("Redelivered order.created with the same event id leaves the existing record untouched")
    void testProcessOrderCreated_RedeliveredEventIsIgnored() {
        UUID eventId = UUID.randomUUID();
        DispatchAssignment existing = existingUnassigned(eventId);
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        DispatchAssignment result = matchingEngine.processOrderCreated(event(eventId, OrderSpeed.STANDARD));

        assertThat(result).isSameAs(existing);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A second order.created for an already registered order is ignored, whatever its event id")
    void testProcessOrderCreated_DuplicateOrderIsIgnored() {
        DispatchAssignment existing = existingUnassigned(UUID.randomUUID());
        when(assignmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        DispatchAssignment result = matchingEngine.processOrderCreated(event(UUID.randomUUID(), OrderSpeed.STANDARD));

        assertThat(result).isSameAs(existing);
        assertThat(result.getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        verify(assignmentRepository, never()).save(any());
    }

    private DispatchAssignment existingUnassigned(UUID eventId) {
        DispatchAssignment existing = new DispatchAssignment();
        existing.setOrderId(orderId);
        existing.setStatus(AssignmentStatus.UNASSIGNED);
        existing.setLastOrderCreatedEventId(eventId);
        return existing;
    }

    private OrderCreatedEvent event(UUID eventId, OrderSpeed speed) {
        return new OrderCreatedEvent(
                eventId,
                UUID.randomUUID(),
                orderId,
                "SD-COL-12345",
                customerId,
                merchantId,
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.GAMPAHA,
                speed,
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(450),
                "LKR",
                Instant.now(),
                new BigDecimal("30"),
                new BigDecimal("20"),
                new BigDecimal("10"),
                new BigDecimal("2.5")
        );
    }
}
