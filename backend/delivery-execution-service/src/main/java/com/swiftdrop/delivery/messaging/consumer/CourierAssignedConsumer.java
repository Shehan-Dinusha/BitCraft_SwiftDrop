package com.swiftdrop.delivery.messaging.consumer;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.delivery.config.RabbitMQConfig;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CourierAssignedConsumer {

    private static final Logger log = LoggerFactory.getLogger(CourierAssignedConsumer.class);

    private final DeliveryExecutionService deliveryExecutionService;

    public CourierAssignedConsumer(DeliveryExecutionService deliveryExecutionService) {
        this.deliveryExecutionService = deliveryExecutionService;
    }

    @RabbitListener(queues = RabbitMQConfig.COURIER_ASSIGNED_QUEUE)
    public void onCourierAssigned(CourierAssignedEvent event) {
        log.info("Received CourierAssignedEvent for order: {}, courier: {}", event.orderId(), event.courierId());
        try {
            deliveryExecutionService.handleCourierAssigned(event);
        } catch (RuntimeException ex) {
            // Previously swallowed, which acked and silently lost the assignment. Reject without
            // requeue instead: the failure is visible to the listener container, and once the queue
            // gets a dead-letter exchange the message is kept there for replay. (Requeueing would
            // loop forever because this queue has no retry policy.)
            log.error("Failed to process courier assignment in delivery service for order: {}", event.orderId(), ex);
            throw new AmqpRejectAndDontRequeueException("Courier assignment could not be processed", ex);
        }
    }
}
