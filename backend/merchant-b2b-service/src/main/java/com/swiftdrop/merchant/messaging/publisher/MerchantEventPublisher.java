package com.swiftdrop.merchant.messaging.publisher;

import com.swiftdrop.contracts.events.EventRoutingKeys;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.merchant.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import com.swiftdrop.merchant.messaging.event.MerchantApprovedEvent;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMerchantApproved(MerchantApprovedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, MerchantApprovedEvent.ROUTING_KEY,
                event, message -> {
                    message.getMessageProperties().setMessageId(event.eventId().toString());
                    message.getMessageProperties().setCorrelationId(event.correlationId().toString());
                    return message;
                });
    }

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
