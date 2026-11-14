package com.swiftdrop.delivery.statemachine.actions;

import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.delivery.messaging.publisher.DeliveryEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublishStateTransitionActionTest {

    @Mock
    private DeliveryEventPublisher eventPublisher;

    private PublishStateTransitionAction action;

    @BeforeEach
    void setUp() {
        action = new PublishStateTransitionAction(eventPublisher);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Publishes immediately when no active database transaction exists")
    void testPublishesImmediatelyWithoutTransaction() {
        UUID deliveryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        action.execute(deliveryId, orderId, "SD-COL-001", courierId,
                DeliveryState.OUT_FOR_DELIVERY, DeliveryState.DELIVERED,
                "Delivered to recipient", SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, null);

        verify(eventPublisher).publishStateChanged(any(DeliveryStateChangedEvent.class));
        verify(eventPublisher).publishParcelStatusChanged(any(ParcelStatusChangedEvent.class));
    }

    @Test
    @DisplayName("Defers event publishing until active transaction commits")
    void testDefersPublishingUntilTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        UUID deliveryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        action.execute(deliveryId, orderId, "SD-COL-001", courierId,
                DeliveryState.OUT_FOR_DELIVERY, DeliveryState.DELIVERED,
                "Delivered to recipient", SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, null);

        // Before commit: nothing should be published
        verify(eventPublisher, never()).publishStateChanged(any());
        verify(eventPublisher, never()).publishParcelStatusChanged(any());

        // Simulate successful transaction commit
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        // After commit: events must be published
        ArgumentCaptor<DeliveryStateChangedEvent> stateCaptor = ArgumentCaptor.forClass(DeliveryStateChangedEvent.class);
        ArgumentCaptor<ParcelStatusChangedEvent> parcelCaptor = ArgumentCaptor.forClass(ParcelStatusChangedEvent.class);

        verify(eventPublisher).publishStateChanged(stateCaptor.capture());
        verify(eventPublisher).publishParcelStatusChanged(parcelCaptor.capture());

        assertThat(stateCaptor.getValue().newState()).isEqualTo(DeliveryState.DELIVERED);
        assertThat(parcelCaptor.getValue().newPublicStatus()).isEqualTo(PublicOrderStatus.DELIVERED);
        assertThat(parcelCaptor.getValue().trackingNumber()).isEqualTo("SD-COL-001");
    }

    @Test
    @DisplayName("Rejected transaction emits nothing if afterCommit is not called")
    void testRejectedTransactionEmitsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        UUID deliveryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        action.execute(deliveryId, orderId, "SD-COL-001", courierId,
                DeliveryState.OUT_FOR_DELIVERY, DeliveryState.FAILED,
                "Delivery rejected", SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO,
                DeliveryFailureReason.CUSTOMER_UNAVAILABLE);

        // Transaction rolls back or throws; clear synchronization without calling afterCommit
        TransactionSynchronizationManager.clearSynchronization();

        // Nothing must have been published
        verify(eventPublisher, never()).publishStateChanged(any());
        verify(eventPublisher, never()).publishParcelStatusChanged(any());
    }

    @Test
    @DisplayName("Propagates non-null customerId to ParcelStatusChangedEvent for notification routing")
    void testPropagatesCustomerIdToParcelStatusChangedEvent() {
        UUID deliveryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        action.execute(deliveryId, orderId, "SD-COL-001", courierId, customerId,
                DeliveryState.OUT_FOR_DELIVERY, DeliveryState.DELIVERED,
                "Delivered to recipient", SriLankanDistrict.COLOMBO, SriLankanDistrict.COLOMBO, null);

        ArgumentCaptor<ParcelStatusChangedEvent> parcelCaptor = ArgumentCaptor.forClass(ParcelStatusChangedEvent.class);
        verify(eventPublisher).publishParcelStatusChanged(parcelCaptor.capture());

        assertThat(parcelCaptor.getValue().customerId()).isEqualTo(customerId);
        assertThat(parcelCaptor.getValue().orderId()).isEqualTo(orderId);
        assertThat(parcelCaptor.getValue().trackingNumber()).isEqualTo("SD-COL-001");
        assertThat(parcelCaptor.getValue().newPublicStatus()).isEqualTo(PublicOrderStatus.DELIVERED);
    }
}

