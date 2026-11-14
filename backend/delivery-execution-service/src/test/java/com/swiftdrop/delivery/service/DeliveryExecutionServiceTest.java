package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.entity.TrackingBreadcrumb;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.domain.repository.TrackingBreadcrumbRepository;
import com.swiftdrop.delivery.dto.CourierLocationUpdatePayload;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
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
import java.util.List;
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
                any(), eq(DeliveryState.CREATED), eq(DeliveryState.ASSIGNED), any(), any(), any(), any());
    }

    @Test
    @DisplayName("transition to DELIVERED: throws InvalidStateTransitionException if PoD not verified")
    void testTransitionToDelivered_PoDNotVerified_ThrowsException() {
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setOrderId(orderId);
        session.setCurrentState(DeliveryState.OUT_FOR_DELIVERY);

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
        session.setCurrentState(DeliveryState.OUT_FOR_DELIVERY);
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
                any(), any(), eq(DeliveryState.OUT_FOR_DELIVERY), eq(DeliveryState.DELIVERED), any(), any(), any(), any());
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

    @Test
    @DisplayName("getDeliveryById with caller: Courier A reading Courier B throws ForbiddenException")
    void testGetDeliveryByIdForbiddenForOtherCourier() {
        UUID courierB = UUID.randomUUID();
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setCourierId(courierB);

        when(deliverySessionRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(session));

        UUID courierA = UUID.randomUUID();
        CallerContext caller = new CallerContext(courierA, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deliveryService.getDeliveryById(deliveryId, caller))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getDeliveryById with caller: Courier A reading own delivery succeeds")
    void testGetDeliveryByIdAllowedForOwnerCourier() {
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setCourierId(courierId);
        session.setOrderId(orderId);
        session.setTrackingNumber("SD-COL-12345");

        when(deliverySessionRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(session));

        CallerContext caller = new CallerContext(courierId, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        var response = deliveryService.getDeliveryById(deliveryId, caller);
        assertThat(response).isNotNull();
        assertThat(response.deliveryId()).isEqualTo(deliveryId);
    }

    @Test
    @DisplayName("transition with caller: Courier A updating Courier B throws ForbiddenException")
    void testTransitionForbiddenForOtherCourier() {
        UUID courierB = UUID.randomUUID();
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setCourierId(courierB);
        session.setCurrentState(DeliveryState.ASSIGNED);

        when(deliverySessionRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(session));

        UUID courierA = UUID.randomUUID();
        CallerContext caller = new CallerContext(courierA, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        TransitionDeliveryRequest request = new TransitionDeliveryRequest(
                DeliveryState.PICKED_UP,
                "Picked up parcel",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                null,
                "COURIER"
        );

        assertThatThrownBy(() -> deliveryService.transition(deliveryId, request, caller))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("recordLocation with caller: Courier A updating Courier B throws ForbiddenException")
    void testRecordLocationForbiddenForOtherCourier() {
        UUID courierB = UUID.randomUUID();
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setCourierId(courierB);

        when(deliverySessionRepository.findByDeliveryId(deliveryId)).thenReturn(Optional.of(session));

        UUID courierA = UUID.randomUUID();
        CallerContext caller = new CallerContext(courierA, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        CourierLocationUpdatePayload payload = new CourierLocationUpdatePayload(
                6.9271, 79.8612, 90.0, 30.0, 3.5
        );

        assertThatThrownBy(() -> deliveryService.recordLocation(deliveryId, payload, caller))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getMyDeliveries returns courier's deliveries for today")
    void testGetMyDeliveriesSuccess() {
        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setCourierId(courierId);
        session.setTrackingNumber("SD-COL-112233");
        session.setCurrentState(DeliveryState.ASSIGNED);

        when(deliverySessionRepository.findByCourierIdAndDateRange(eq(courierId), any(), any()))
                .thenReturn(List.of(session));

        CallerContext caller = new CallerContext(courierId, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        var list = deliveryService.getMyDeliveries(caller, "today");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).deliveryId()).isEqualTo(deliveryId);
        assertThat(list.get(0).courierId()).isEqualTo(courierId);
    }

    @Test
    @DisplayName("getMyDeliveries with invalid date format throws IllegalArgumentException")
    void testGetMyDeliveriesInvalidDateFormat() {
        CallerContext caller = new CallerContext(courierId, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);

        assertThatThrownBy(() -> deliveryService.getMyDeliveries(caller, "not-a-valid-date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date format");
    }

    @Test
    @DisplayName("getMyDeliveries without caller throws UnauthenticatedCallerException")
    void testGetMyDeliveriesNullCaller() {
        assertThatThrownBy(() -> deliveryService.getMyDeliveries((CallerContext) null, "today"))
                .isInstanceOf(com.swiftdrop.contracts.security.UnauthenticatedCallerException.class);
    }
}
