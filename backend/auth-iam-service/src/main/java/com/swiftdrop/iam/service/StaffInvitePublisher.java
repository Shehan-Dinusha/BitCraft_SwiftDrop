package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.events.EventRoutingKeys;
import com.swiftdrop.contracts.events.auth.AuthStaffInviteEvent;
import com.swiftdrop.iam.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes {@link AuthStaffInviteEvent} so notification-service can email the invite link. Inside a
 * transaction the message is sent only after commit, so a rolled-back account creation never emails
 * a link for an account that does not exist.
 *
 * <p>A broker failure is logged but not thrown: the dispatcher can always press "Resend invite".
 * The event (which carries the link) is never logged.
 */
@Slf4j
@Component
public class StaffInvitePublisher {

    private final RabbitTemplate rabbitTemplate;

    public StaffInvitePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(AuthStaffInviteEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(AuthStaffInviteEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, EventRoutingKeys.AUTH_STAFF_INVITE,
                    event, message -> {
                        message.getMessageProperties().setMessageId(event.eventId().toString());
                        message.getMessageProperties().setCorrelationId(event.correlationId().toString());
                        return message;
                    });
            log.info("Published staff invite {} for user {}", event.eventId(), event.recipientId());
        } catch (AmqpException ex) {
            log.error("Could not publish staff invite for user {}: {}", event.recipientId(), ex.getMessage());
        }
    }
}
