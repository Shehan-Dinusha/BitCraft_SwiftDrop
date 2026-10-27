package com.swiftdrop.dispatch.messaging.consumer;

import com.swiftdrop.contracts.events.order.OrderCancelledEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCancelledConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledConsumer.class);

    private final DispatchAssignmentService dispatchAssignmentService;

    public OrderCancelledConsumer(DispatchAssignmentService dispatchAssignmentService) {
        this.dispatchAssignmentService = dispatchAssignmentService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelledEvent for order: {}, reason: {}", event.orderId(), event.reason());
        try {
            dispatchAssignmentService.handleOrderCancelled(event);
        } catch (Exception ex) {
            log.error("Failed to process order cancellation in dispatch for order: {}", event.orderId(), ex);
        }
    }
}