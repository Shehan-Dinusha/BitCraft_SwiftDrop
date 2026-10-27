package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.entity.TrackingBreadcrumb;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.domain.repository.TrackingBreadcrumbRepository;
import com.swiftdrop.delivery.dto.CourierLocationUpdatePayload;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.exception.DeliveryNotFoundException;
import com.swiftdrop.delivery.exception.InvalidStateTransitionException;
import com.swiftdrop.delivery.messaging.publisher.DeliveryEventPublisher;
import com.swiftdrop.delivery.statemachine.DeliveryStateMachine;
import com.swiftdrop.delivery.statemachine.actions.AuditTransitionAction;
import com.swiftdrop.delivery.statemachine.actions.PublishStateTransitionAction;
import com.swiftdrop.delivery.statemachine.guards.ProofOfDeliveryGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryExecutionServiceTest {

    @Mock
    private DeliverySessionRepository deliverySessionRepository;

    @Mock
    private TrackingBreadcrumbRepository breadcrumbRepository;

    @Spy
    private DeliveryStateMachine stateMachine = new DeliveryStateMachine();

    @Mock
    private ProofOfDeliveryGuard proofOfDeliveryGuard;

    @Mock
    private AuditTransitionAction auditTransitionAction;

    @Mock
    private PublishStateTransitionAction publishStateTransitionAction;

    @Mock
    private DeliveryEventPublisher eventPublisher;

    @InjectMocks
    private DeliveryExecutionService deliveryService;

    private UUID deliveryId;
    private UUID orderId;
    private UUID courierId;

    @BeforeEach
    void setUp() {
        deliveryId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        courierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("handleCourierAssigned: transitions delivery to ASSIGNED and triggers events")
    void testHandleCourierAssigned() {
        CourierAssignedEvent event = new CourierAssignedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderId,
                courierId,
                "Sunil Perera",
                25,
                Instant.now(),
                Instant.now()
        );

        when(deliverySessionRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(deliverySessionRepository.save(any(DeliverySession.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliverySession session = deliveryService.handleCourierAssigned(event);

        assertThat(session.getCurrentState()).isEqualTo(DeliveryState.ASSIGNED);
        assertThat(session.getCourierId()).isEqualTo(courierId);
        assertThat(session.getCourierName()).isEqualTo("Sunil Perera");

        verify(auditTransitionAction).execute(any(), eq(orderId), eq(DeliveryState.CREATED),
                eq(DeliveryState.ASSIGNED), any(), any(), any(), any(), any());
        verify(publishStateTransitionAction).execute(any(), eq(orderId), any(), eq(courierId),
                eq(DeliveryState.CREATED), eq(DeliveryState.ASSIGNED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("transition to DELIVERED: throws InvalidStateTransitionException if PoD not verified")
    void testTransitionToDelivered_PoDNotVerified_ThrowsException() {
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setOrderId(orderId);
        session.setCurrentState(DeliveryState.IN_TRANSIT);

        when(deliverySessionRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(session));
        when(proofOfDeliveryGuard.isSatisfied(deliveryId)).thenReturn(false);

        TransitionDeliveryRequest request = new TransitionDeliveryRequest(
                DeliveryState.DELIVERED,
                "Handed over to customer",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                null,
                "COURIER"
        );

        assertThatThrownBy(() -> deliveryService.transition(deliveryId, request))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Proof of delivery must be verified");
    }

    @Test
    @DisplayName("transition to DELIVERED: succeeds when PoD is verified")
    void testTransitionToDelivered_PoDVerified_Success() {
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setOrderId(orderId);
        session.setCurrentState(DeliveryState.IN_TRANSIT);
        session.setPickupDistrict(SriLankanDistrict.COLOMBO);
        session.setDeliveryDistrict(SriLankanDistrict.COLOMBO);
        session.setTrackingNumber("SD-COL-12345");

        when(deliverySessionRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(session));
        when(proofOfDeliveryGuard.isSatisfied(deliveryId)).thenReturn(true);
        when(deliverySessionRepository.save(any(DeliverySession.class))).thenAnswer(inv -> inv.getArgument(0));

        TransitionDeliveryRequest request = new TransitionDeliveryRequest(
                DeliveryState.DELIVERED,
                "Handed over with signature & OTP",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                null,
                "COURIER"
        );

        DeliverySession result = deliveryService.transition(deliveryId, request);

        assertThat(result.getCurrentState()).isEqualTo(DeliveryState.DELIVERED);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getDeliveredAt()).isNotNull();

        verify(publishStateTransitionAction).execute(eq(deliveryId), eq(orderId), eq("SD-COL-12345"),
                any(), eq(DeliveryState.IN_TRANSIT), eq(DeliveryState.DELIVERED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("recordLocation: stores breadcrumb and publishes LocationUpdatedEvent")
    void testRecordLocation() {
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setCourierId(courierId);

        when(deliverySessionRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(session));
        when(breadcrumbRepository.save(any(TrackingBreadcrumb.class))).thenAnswer(inv -> inv.getArgument(0));

        CourierLocationUpdatePayload payload = new CourierLocationUpdatePayload(
                6.9271,
                79.8612,
                90.0,
                30.0,
                3.5
        );

        TrackingBreadcrumb breadcrumb = deliveryService.recordLocation(deliveryId, payload);

        assertThat(breadcrumb).isNotNull();
        assertThat(breadcrumb.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(breadcrumb.getCourierId()).isEqualTo(courierId);
        assertThat(breadcrumb.getLatitude()).isEqualTo(BigDecimal.valueOf(6.9271));

        verify(eventPublisher).publishLocationUpdated(argThat(e ->
                e.deliveryId().equals(deliveryId) && e.latitude() == 6.9271
        ));
    }
}
