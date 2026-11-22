package com.swiftdrop.delivery.service;

import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The two routes: same district straight to the recipient, different districts through both hubs. */
@ExtendWith(MockitoExtension.class)
class HubRouteServiceTest {

    @Mock private DeliverySessionRepository repository;
    @Spy private DeliveryStateMachine stateMachine = new DeliveryStateMachine();
    @Mock private ProofOfDeliveryGuard proofOfDeliveryGuard;
    @Mock private AuditTransitionAction audit;
    @Mock private PublishStateTransitionAction publish;
    @Mock private DeliveryEventPublisher eventPublisher;
    @InjectMocks private DeliveryExecutionService service;

    private final UUID firstCourier = UUID.randomUUID();
    private DeliverySession session;

    @BeforeEach
    void colomboToKandy() {
        session = new DeliverySession();
        session.setDeliveryId(UUID.randomUUID());
        session.setOrderId(UUID.randomUUID());
        session.setCourierId(firstCourier);
        session.setPickupDistrict(SriLankanDistrict.COLOMBO);
        session.setDeliveryDistrict(SriLankanDistrict.KANDY);
        session.setCurrentState(DeliveryState.PICKED_UP);
        when(repository.findByDeliveryId(session.getDeliveryId())).thenReturn(Optional.of(session));
    }

    private void stubSave() {
        when(repository.save(any(DeliverySession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static TransitionDeliveryRequest to(DeliveryState target) {
        return new TransitionDeliveryRequest(target, null, null, null);
    }

    private static CallerContext dispatcherOf(SriLankanDistrict district) {
        return new CallerContext(UUID.randomUUID(), UserRole.DISPATCHER, null, district);
    }

    private CallerContext theFirstCourier() {
        return new CallerContext(firstCourier, UserRole.COURIER, null, SriLankanDistrict.COLOMBO);
    }

    @Test
    @DisplayName("A cross-district parcel goes hub to hub, and the first courier is freed at the origin hub")
    void crossDistrictJourney() {
        stubSave();

        service.transition(session.getDeliveryId(), to(DeliveryState.AT_ORIGIN_HUB), theFirstCourier());
        assertThat(session.getCurrentState()).isEqualTo(DeliveryState.AT_ORIGIN_HUB);
        assertThat(session.getCourierId()).isNull();

        service.transition(session.getDeliveryId(), to(DeliveryState.IN_TRANSIT), dispatcherOf(SriLankanDistrict.COLOMBO));
        service.transition(session.getDeliveryId(), to(DeliveryState.AT_DESTINATION_HUB), dispatcherOf(SriLankanDistrict.KANDY));
        assertThat(session.getCurrentState()).isEqualTo(DeliveryState.AT_DESTINATION_HUB);

        // The Kandy dispatcher assigns a Kandy courier: the parcel stays at the hub until collected.
        UUID lastMile = UUID.randomUUID();
        when(repository.findByOrderId(session.getOrderId())).thenReturn(Optional.of(session));
        service.handleCourierAssigned(new CourierAssignedEvent(UUID.randomUUID(), UUID.randomUUID(),
                session.getOrderId(), lastMile, "Kandy Courier", 20, Instant.now(), Instant.now()));
        assertThat(session.getCurrentState()).isEqualTo(DeliveryState.AT_DESTINATION_HUB);
        assertThat(session.getCourierId()).isEqualTo(lastMile);

        service.transition(session.getDeliveryId(), to(DeliveryState.OUT_FOR_DELIVERY),
                new CallerContext(lastMile, UserRole.COURIER, null, SriLankanDistrict.KANDY));
        assertThat(session.getCurrentState()).isEqualTo(DeliveryState.OUT_FOR_DELIVERY);
        verify(publish).execute(any(), any(), any(), any(), any(), eq(DeliveryState.IN_TRANSIT),
                eq(DeliveryState.AT_DESTINATION_HUB), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A cross-district parcel cannot skip the hubs")
    void crossDistrictCannotSkipTheHubs() {
        assertThatThrownBy(() -> service.transition(session.getDeliveryId(), to(DeliveryState.IN_TRANSIT), theFirstCourier()))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("hand it in at the hub");

        session.setCurrentState(DeliveryState.IN_TRANSIT);
        assertThatThrownBy(() -> service.transition(session.getDeliveryId(), to(DeliveryState.OUT_FOR_DELIVERY), null))
                .isInstanceOf(InvalidStateTransitionException.class);

        session.setCurrentState(DeliveryState.AT_DESTINATION_HUB);
        session.setCourierId(null);
        assertThatThrownBy(() -> service.transition(session.getDeliveryId(), to(DeliveryState.OUT_FOR_DELIVERY), null))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Assign a courier");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("A same-district parcel never goes through a hub")
    void sameDistrictGoesStraightOn() {
        session.setDeliveryDistrict(SriLankanDistrict.COLOMBO);
        assertThatThrownBy(() -> service.transition(session.getDeliveryId(), to(DeliveryState.AT_ORIGIN_HUB), theFirstCourier()))
                .isInstanceOf(InvalidStateTransitionException.class);

        stubSave();
        service.transition(session.getDeliveryId(), to(DeliveryState.IN_TRANSIT), theFirstCourier());
        assertThat(session.getCurrentState()).isEqualTo(DeliveryState.IN_TRANSIT);
        assertThat(session.getCourierId()).isEqualTo(firstCourier);
    }

    @Test
    @DisplayName("Only the right district's dispatcher takes each hub step; couriers never do")
    void hubStepsBelongToTheRightDispatcher() {
        session.setCurrentState(DeliveryState.AT_ORIGIN_HUB);
        session.setCourierId(null);
        assertThatThrownBy(() -> service.transition(session.getDeliveryId(), to(DeliveryState.IN_TRANSIT),
                dispatcherOf(SriLankanDistrict.KANDY)))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("Outbound");

        session.setCurrentState(DeliveryState.IN_TRANSIT);
        assertThatThrownBy(() -> service.transition(session.getDeliveryId(), to(DeliveryState.AT_DESTINATION_HUB),
                dispatcherOf(SriLankanDistrict.COLOMBO)))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("accept");
        verify(repository, never()).save(any());

        stubSave();
        CallerContext admin = new CallerContext(UUID.randomUUID(), UserRole.SYSTEM_ADMIN, null, null);
        service.transition(session.getDeliveryId(), to(DeliveryState.AT_DESTINATION_HUB), admin);
        assertThat(session.getCurrentState()).isEqualTo(DeliveryState.AT_DESTINATION_HUB);
    }
}
