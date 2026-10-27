package com.swiftdrop.merchant.messaging.publisher;

import com.swiftdrop.contracts.events.EventRoutingKeys;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.merchant.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing OrderCreatedEvent for ingested merchant order: {}, trackingNumber: {}",
                event.orderId(), event.trackingNumber());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                EventRoutingKeys.ORDER_CREATED,
                event
        );
    }
}
