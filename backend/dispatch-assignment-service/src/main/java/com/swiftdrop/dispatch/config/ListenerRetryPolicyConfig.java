package com.swiftdrop.dispatch.config;

import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;
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
 * <p>Permanent failures are not retried: retrying cannot fix a malformed message,
 * invalid event data or an impossible lifecycle transition. Every other failure
 * (for example a database outage or a lock conflict) is treated as transient and
 * retried with backoff. Either way, once retrying stops the message is rejected
 * without requeue, so the queue's {@code x-dead-letter-exchange} routes it to
 * {@code dlq.dispatch.events}. This bound works on every broker, including AKS,
 * where no quorum-queue delivery-limit policy is configured.</p>
 */
@Configuration
public class ListenerRetryPolicyConfig {

    static final Map<Class<? extends Throwable>, Boolean> PERMANENT_FAILURES = Map.of(
            org.springframework.amqp.support.converter.MessageConversionException.class, false,
            org.springframework.messaging.converter.MessageConversionException.class, false,
            AmqpRejectAndDontRequeueException.class, false,
            InvalidAssignmentTransitionException.class, false,
            IllegalArgumentException.class, false,
            DataIntegrityViolationException.class, false
    );

    @Bean
    public RabbitRetryTemplateCustomizer dispatchListenerRetryClassifier(RabbitProperties rabbitProperties) {
        int maxAttempts = rabbitProperties.getListener().getSimple().getRetry().getMaxAttempts();
        return (target, retryTemplate) -> {
            if (target == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                retryTemplate.setRetryPolicy(listenerRetryPolicy(maxAttempts));
            }
        };
    }

    /**
     * Retries unclassified failures up to {@code maxAttempts}; permanent failures
     * stop after the first attempt. Causes are traversed because listener
     * exceptions arrive wrapped in {@code ListenerExecutionFailedException}.
     */
    static SimpleRetryPolicy listenerRetryPolicy(int maxAttempts) {
        return new SimpleRetryPolicy(maxAttempts, PERMANENT_FAILURES, true, true);
    }
}
