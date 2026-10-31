package com.swiftdrop.dispatch.messaging.consumer;

import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.service.CourierMatchingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final CourierMatchingEngine matchingEngine;

    public OrderCreatedConsumer(CourierMatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATED_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order: {}, tracking: {}, pickup: {}",
                event.orderId(), event.trackingNumber(), event.pickupDistrict());
        try {
            matchingEngine.processOrderCreated(event);
        } catch (Exception ex) {
            log.error("Failed to process order creation in dispatch engine for order: {}", event.orderId(), ex);
        }
    }
}
