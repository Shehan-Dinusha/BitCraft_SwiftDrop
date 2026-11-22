package com.swiftdrop.dispatch.messaging.consumer;

import com.swiftdrop.contracts.events.order.OrderUpdatedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderUpdatedConsumer.class);

    private final DispatchAssignmentService dispatchAssignmentService;

    public OrderUpdatedConsumer(DispatchAssignmentService dispatchAssignmentService) {
        this.dispatchAssignmentService = dispatchAssignmentService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_UPDATED_QUEUE)
    public void onOrderUpdated(OrderUpdatedEvent event) {
        log.info("Received OrderUpdatedEvent for order: {}", event.orderId());
        if (event.orderId() == null) {
            // Permanent: retrying cannot repair the event, so dead-letter it immediately.
            throw new AmqpRejectAndDontRequeueException(
                    "order.updated event " + event.eventId() + " has no orderId");
        }
        try {
            dispatchAssignmentService.handleOrderUpdated(event);
        } catch (RuntimeException ex) {
            // Log with the order context, then rethrow so the retry policy and DLQ take over.
            log.error("Failed to process order update in dispatch for order: {}", event.orderId(), ex);
            throw ex;
        }
    }
}
