package com.swiftdrop.order.messaging.publisher;

import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.events.EventRoutingKeys;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCancelledEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.order.config.RabbitMQConfig;
import com.swiftdrop.order.domain.entity.CustomerOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreated(CustomerOrder order, UUID correlationId) {
        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();

        OrderCreatedEvent createdEvent = new OrderCreatedEvent(
                UUID.randomUUID(),
                effectiveCorrelationId,
                order.getOrderId(),
                order.getTrackingNumber(),
                order.getCustomerId(),
                order.getMerchantId(),
                order.getPickupDistrict(),
                order.getDeliveryDistrict(),
                order.getOrderSpeed(),
                order.getCodAmount(),
                order.getDeclaredValue(),
                order.getEstimatedFee(),
                order.getCurrency(),
                Instant.now()
        );

        log.info("Publishing OrderCreatedEvent for orderId={}, trackingNumber={}, correlationId={}",
                order.getOrderId(), order.getTrackingNumber(), effectiveCorrelationId);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                EventRoutingKeys.ORDER_CREATED,
                createdEvent
        );

        // Also publish initial ParcelStatusChangedEvent so tracking starts immediately
        ParcelStatusChangedEvent statusEvent = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                effectiveCorrelationId,
                order.getTrackingNumber(),
                order.getOrderId(),
                order.getCustomerId(),
                null,
                PublicOrderStatus.CREATED,
                InternalQueueStatus.CREATED,
                order.getPickupDistrict(),
                order.getDeliveryDistrict(),
                null,
                Instant.now()
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                EventRoutingKeys.PARCEL_STATUS_CHANGED,
                statusEvent
        );
    }

    public void publishOrderCancelled(CustomerOrder order, String reason, String cancelledBy, UUID correlationId) {
        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();

        OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(
                UUID.randomUUID(),
                effectiveCorrelationId,
                order.getOrderId(),
                order.getCustomerId(),
                reason,
                cancelledBy,
                Instant.now()
        );

        log.info("Publishing OrderCancelledEvent for orderId={}, trackingNumber={}",
                order.getOrderId(), order.getTrackingNumber());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                EventRoutingKeys.ORDER_CANCELLED,
                cancelledEvent
        );

        ParcelStatusChangedEvent statusEvent = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                effectiveCorrelationId,
                order.getTrackingNumber(),
                order.getOrderId(),
                order.getCustomerId(),
                order.getStatus(),
                PublicOrderStatus.CANCELLED,
                InternalQueueStatus.CANCELLED,
                order.getPickupDistrict(),
                order.getDeliveryDistrict(),
                null,
                Instant.now()
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                EventRoutingKeys.PARCEL_STATUS_CHANGED,
                statusEvent
        );
    }

    public void publishStatusChanged(CustomerOrder order, PublicOrderStatus previousStatus, PublicOrderStatus newStatus, UUID correlationId) {
        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();

        ParcelStatusChangedEvent statusEvent = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                effectiveCorrelationId,
                order.getTrackingNumber(),
                order.getOrderId(),
                order.getCustomerId(),
                previousStatus,
                newStatus,
                order.getInternalQueueStatus(),
                order.getPickupDistrict(),
                order.getDeliveryDistrict(),
                null,
                Instant.now()
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                EventRoutingKeys.PARCEL_STATUS_CHANGED,
                statusEvent
        );
    }
}
