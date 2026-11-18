package com.swiftdrop.delivery.messaging.consumer;

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
            // Rethrow the original exception (never swallow it, which would ack and lose the
            // assignment). ListenerRetryPolicyConfig retries transient failures; once it gives up,
            // the message is rejected and dead-lettered to dlq.delivery.events.queue.
            log.error("Failed to process courier assignment for order: {}", event.orderId(), ex);
            throw ex;
        }
    }
}
