package com.swiftdrop.iam.service;

import com.swiftdrop.contracts.events.EventRoutingKeys;
import com.swiftdrop.contracts.events.auth.AuthLoginEvent;
import com.swiftdrop.contracts.events.auth.AuthLoginEvent.LoginOutcome;
import com.swiftdrop.iam.config.RabbitMQConfig;
import com.swiftdrop.iam.domain.entity.UserAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes {@link AuthLoginEvent} so notification-service can email a security alert for a new
 * sign-in or a wrong-password attempt. Publishing never fails the sign-in itself.
 */
@Slf4j
@Component
public class AuthLoginPublisher {

    private static final int MAX_USER_AGENT = 300;

    private final RabbitTemplate rabbitTemplate;

    public AuthLoginPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** A sign-in completed: sent after the transaction commits, so a rolled-back sign-in sends nothing. */
    public void publishSuccess(UserAccount user, String ipAddress, String userAgent) {
        AuthLoginEvent event = event(user, LoginOutcome.SUCCESS, ipAddress, userAgent);
        if (event == null) {
            return;
        }
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

    /**
     * A wrong password for an existing account. Sent straight away: the failing request rolls its
     * transaction back, so an after-commit hook would never run.
     */
    public void publishFailure(UserAccount user, String ipAddress, String userAgent) {
        AuthLoginEvent event = event(user, LoginOutcome.FAILED, ipAddress, userAgent);
        if (event != null) {
            send(event);
        }
    }

    private static AuthLoginEvent event(UserAccount user, LoginOutcome outcome, String ipAddress, String userAgent) {
        if (user == null || user.getPublicId() == null) {
            return null;
        }
        String agent = userAgent == null ? null
                : userAgent.length() > MAX_USER_AGENT ? userAgent.substring(0, MAX_USER_AGENT) : userAgent;
        return new AuthLoginEvent(null, null, user.getPublicId(), outcome, ipAddress, agent, null, null);
    }

    private void send(AuthLoginEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, EventRoutingKeys.AUTH_LOGIN, event, message -> {
                message.getMessageProperties().setMessageId(event.eventId().toString());
                message.getMessageProperties().setCorrelationId(event.correlationId().toString());
                return message;
            });
            log.info("Published {} sign-in alert {} for user {}", event.outcome(), event.eventId(), event.recipientId());
        } catch (AmqpException ex) {
            log.error("Could not publish {} sign-in alert for user {}: {}", event.outcome(), event.recipientId(), ex.getMessage());
        }
    }
}
