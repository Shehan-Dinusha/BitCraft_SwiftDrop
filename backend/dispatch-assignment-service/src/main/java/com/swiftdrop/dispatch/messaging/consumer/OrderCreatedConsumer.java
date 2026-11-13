package com.swiftdrop.dispatch.messaging.consumer;

import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.service.CourierMatchingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final CourierMatchingEngine matchingEngine;
    private final DispatchAssignmentRepository assignmentRepository;

    public OrderCreatedConsumer(
            CourierMatchingEngine matchingEngine,
            DispatchAssignmentRepository assignmentRepository) {
        this.matchingEngine = matchingEngine;
        this.assignmentRepository = assignmentRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATED_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order: {}, tracking: {}, pickup: {}",
                event.orderId(), event.trackingNumber(), event.pickupDistrict());
        try {
            matchingEngine.processOrderCreated(event);
        } catch (DataIntegrityViolationException ex) {
            if (assignmentRepository.findByOrderId(event.orderId()).isPresent()) {
                log.info("Ignoring duplicate order.created event for existing order: {}", event.orderId());
                return;
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to process order creation in dispatch engine for order: {}", event.orderId(), ex);
            throw ex;
        }
    }
}
