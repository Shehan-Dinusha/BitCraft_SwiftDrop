package com.swiftdrop.delivery.statemachine.actions;

import com.swiftdrop.contracts.enums.DeliveryFailureReason;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.DeliveryStateChangedEvent;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.delivery.messaging.publisher.DeliveryEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Action that broadcasts delivery state change events to internal and public
 * event topics.
 * Defers publishing until the active transaction commits to prevent ghost
 * events on rollback.
 */
@Component
public class PublishStateTransitionAction {

    private final DeliveryEventPublisher eventPublisher;

    public PublishStateTransitionAction(DeliveryEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void execute(UUID deliveryId, UUID orderId, String trackingNumber, UUID courierId, UUID customerId,
            DeliveryState previousState, DeliveryState newState, String notes,
            SriLankanDistrict pickupDistrict, SriLankanDistrict deliveryDistrict,
            DeliveryFailureReason failureReason) {
        execute(deliveryId, orderId, trackingNumber, courierId, customerId, previousState, newState, notes,
                pickupDistrict, deliveryDistrict, failureReason, null);
    }

    /**
     * @param correlationId carried on both events, so a transition caused by another service's event (a courier
     *                      assignment) traces back to it; a new one is generated when {@code null}
     */
    public void execute(UUID deliveryId, UUID orderId, String trackingNumber, UUID courierId, UUID customerId,
            DeliveryState previousState, DeliveryState newState, String notes,
            SriLankanDistrict pickupDistrict, SriLankanDistrict deliveryDistrict,
            DeliveryFailureReason failureReason, UUID correlationId) {

        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();
        Runnable publishTask = () -> doPublish(deliveryId, orderId, trackingNumber, courierId, customerId,
                previousState, newState, notes, pickupDistrict, deliveryDistrict, failureReason,
                effectiveCorrelationId);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTask.run();
                }
            });
        } else {
            publishTask.run();
        }
    }

    /**
     * Backward-compatible overload for callers without customerId.
     */
    public void execute(UUID deliveryId, UUID orderId, String trackingNumber, UUID courierId,
            DeliveryState previousState, DeliveryState newState, String notes,
            SriLankanDistrict pickupDistrict, SriLankanDistrict deliveryDistrict,
            DeliveryFailureReason failureReason) {
        execute(deliveryId, orderId, trackingNumber, courierId, null,
                previousState, newState, notes, pickupDistrict, deliveryDistrict, failureReason);
    }

    private void doPublish(UUID deliveryId, UUID orderId, String trackingNumber, UUID courierId, UUID customerId,
            DeliveryState previousState, DeliveryState newState, String notes,
            SriLankanDistrict pickupDistrict, SriLankanDistrict deliveryDistrict,
            DeliveryFailureReason failureReason, UUID correlationId) {

        // 1. Publish internal delivery state changed event
        eventPublisher.publishStateChanged(new DeliveryStateChangedEvent(
                UUID.randomUUID(),
                correlationId,
                deliveryId,
                orderId,
                courierId,
                previousState,
                newState,
                notes,
                Instant.now()));

        // 2. Map and publish public tracking event for Gateway / Customer streams
        PublicOrderStatus publicStatus = PublicOrderStatus.fromDeliveryState(newState);
        PublicOrderStatus prevPublicStatus = PublicOrderStatus.fromDeliveryState(previousState);
        InternalQueueStatus internalSubStatus = InternalQueueStatus.fromDeliveryState(newState);

        eventPublisher.publishParcelStatusChanged(new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                correlationId,
                trackingNumber,
                orderId,
                customerId,
                prevPublicStatus,
                publicStatus,
                internalSubStatus,
                pickupDistrict,
                deliveryDistrict,
                failureReason,
                Instant.now()));
    }
}
