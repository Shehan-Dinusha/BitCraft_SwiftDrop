package com.swiftdrop.delivery.config;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

/**
 * Classifies RabbitMQ listener failures for the bounded retry configured under
 * {@code spring.rabbitmq.listener.simple.retry}.
 *
 * <p>Permanent failures (a malformed message, invalid event data, a constraint violation) are not
 * retried, because retrying cannot fix them. Everything else, such as a database outage or a lock
 * conflict, is retried with backoff. When retrying stops, the message is rejected without requeue
 * and the queue's {@code x-dead-letter-exchange} moves it to {@code dlq.delivery.events.queue}.
 * The handler is transactional, so a failed attempt leaves no partial state behind.
 */
@Configuration
public class ListenerRetryPolicyConfig {

    static final Map<Class<? extends Throwable>, Boolean> PERMANENT_FAILURES = Map.of(
            org.springframework.amqp.support.converter.MessageConversionException.class, false,
            org.springframework.messaging.converter.MessageConversionException.class, false,
            AmqpRejectAndDontRequeueException.class, false,
            IllegalArgumentException.class, false,
            DataIntegrityViolationException.class, false
    );

    @Bean
    public RabbitRetryTemplateCustomizer deliveryListenerRetryClassifier(RabbitProperties rabbitProperties) {
        int maxAttempts = rabbitProperties.getListener().getSimple().getRetry().getMaxAttempts();
        return (target, retryTemplate) -> {
            if (target == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                retryTemplate.setRetryPolicy(listenerRetryPolicy(maxAttempts));
            }
        };
    }

    /**
     * Retries unclassified failures up to {@code maxAttempts}; permanent failures stop after the
     * first attempt. Causes are traversed because listener exceptions arrive wrapped in
     * {@code ListenerExecutionFailedException}.
     */
    static SimpleRetryPolicy listenerRetryPolicy(int maxAttempts) {
        return new SimpleRetryPolicy(maxAttempts, PERMANENT_FAILURES, true, true);
    }
}
