package com.swiftdrop.dispatch.messaging.publisher;

import com.swiftdrop.contracts.events.dispatch.CourierAssignedEvent;
import com.swiftdrop.contracts.events.dispatch.DispatchFailedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Records dispatch events for publication. Messages are handed to
 * {@link AfterCommitRabbitRelay}, which sends them only after the caller's
 * transaction commits.
 */
@Component
public class DispatchEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public DispatchEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishCourierAssigned(CourierAssignedEvent event) {
        log.info("Publishing CourierAssignedEvent for order: {}, courier: {}", event.orderId(), event.courierId());
        enqueue(RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED, event, event.orderId());
    }

    public void publishDispatchFailed(DispatchFailedEvent event) {
        log.warn("Publishing DispatchFailedEvent for order: {}, reason: {}", event.orderId(), event.failureReason());
        enqueue(RabbitMQConfig.ROUTING_KEY_DISPATCH_FAILED, event, event.orderId());
    }

    private void enqueue(String routingKey, Object payload, UUID orderId) {
        applicationEventPublisher.publishEvent(new OutboundDispatchMessage(routingKey, payload, orderId));
    }
}
