package com.swiftdrop.delivery.messaging.consumer;

import com.swiftdrop.contracts.events.order.OrderCancelledEvent;
import com.swiftdrop.delivery.config.RabbitMQConfig;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCancelledConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledConsumer.class);

    private final DeliveryExecutionService deliveryExecutionService;

    public OrderCancelledConsumer(DeliveryExecutionService deliveryExecutionService) {
        this.deliveryExecutionService = deliveryExecutionService;
    }

    /** A failure is rethrown, so the bounded retry and then the dead-letter queue keep the message. */
    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelledEvent for order: {}", event.orderId());
        deliveryExecutionService.handleOrderCancelled(event);
    }
}
