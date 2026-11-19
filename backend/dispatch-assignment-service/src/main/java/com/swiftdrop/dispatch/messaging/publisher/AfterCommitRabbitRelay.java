package com.swiftdrop.dispatch.messaging.publisher;

import com.swiftdrop.dispatch.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends dispatch events to RabbitMQ only after the database transaction that
 * produced them has committed.
 *
 * <p>Events raised inside a transaction that rolls back (for example an
 * optimistic-lock conflict detected at commit) are discarded by Spring and never
 * reach the broker. {@code fallbackExecution = true} keeps non-transactional
 * callers working: they publish immediately.</p>
 *
 * <p>A broker failure after commit cannot undo the committed change, so it is
 * logged instead of being rethrown. Rethrowing would turn an assignment that
 * really succeeded into an HTTP 500 or a redelivered message. Guaranteed
 * delivery would require a transactional outbox, which is outside PR1.</p>
 */
@Component
public class AfterCommitRabbitRelay {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitRabbitRelay.class);

    private final RabbitTemplate rabbitTemplate;

    public AfterCommitRabbitRelay(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(OutboundDispatchMessage message) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, message.routingKey(), message.payload());
            log.debug("Relayed {} for order {} after commit", message.routingKey(), message.orderId());
        } catch (AmqpException ex) {
            log.error("Committed dispatch change for order {} but failed to publish {}",
                    message.orderId(), message.routingKey(), ex);
        }
    }
}
