package com.swiftdrop.delivery.config;

import com.swiftdrop.delivery.domain.entity.DeliverySession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.support.RetryTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListenerRetryPolicyConfigTest {

    private static final int MAX_ATTEMPTS = 3;

    @Test
    @DisplayName("Transient failures are retried up to the configured attempt limit")
    void transientFailuresAreRetried() {
        assertThat(attemptsUntilGivingUp(new TransientDataAccessResourceException("database unavailable")))
                .isEqualTo(MAX_ATTEMPTS);
        assertThat(attemptsUntilGivingUp(new ObjectOptimisticLockingFailureException(DeliverySession.class, 1L)))
                .isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("Permanent failures stop after the first attempt")
    void permanentFailuresAreNotRetried() {
        assertThat(attemptsUntilGivingUp(new MessageConversionException("malformed JSON"))).isEqualTo(1);
        assertThat(attemptsUntilGivingUp(new IllegalArgumentException("bad event"))).isEqualTo(1);
        assertThat(attemptsUntilGivingUp(new DataIntegrityViolationException("null order_id"))).isEqualTo(1);
        assertThat(attemptsUntilGivingUp(new AmqpRejectAndDontRequeueException("reject"))).isEqualTo(1);
    }

    @Test
    @DisplayName("The classifier applies to listener retries only, not to RabbitTemplate retries")
    void customizerOnlyChangesListenerRetries() {
        RetryTemplate senderRetries = new RetryTemplate();
        new ListenerRetryPolicyConfig().deliveryListenerRetryClassifier(rabbitProperties())
                .customize(RabbitRetryTemplateCustomizer.Target.SENDER, senderRetries);

        assertThat(attempts(senderRetries, new IllegalArgumentException("unchanged default")))
                .as("default RetryTemplate policy, untouched").isEqualTo(3);
    }

    private int attemptsUntilGivingUp(RuntimeException cause) {
        RetryTemplate listenerRetries = new RetryTemplate();
        new ListenerRetryPolicyConfig().deliveryListenerRetryClassifier(rabbitProperties())
                .customize(RabbitRetryTemplateCustomizer.Target.LISTENER, listenerRetries);
        return attempts(listenerRetries, cause);
    }

    private int attempts(RetryTemplate retryTemplate, RuntimeException cause) {
        AtomicInteger attempts = new AtomicInteger();
        // The listener container wraps every listener exception like this.
        ListenerExecutionFailedException wrapped = new ListenerExecutionFailedException(
                "Listener threw exception", cause, new Message(new byte[0], new MessageProperties()));

        assertThatThrownBy(() -> retryTemplate.execute(context -> {
            attempts.incrementAndGet();
            throw wrapped;
        })).isSameAs(wrapped);
        return attempts.get();
    }

    private RabbitProperties rabbitProperties() {
        RabbitProperties properties = new RabbitProperties();
        properties.getListener().getSimple().getRetry().setMaxAttempts(MAX_ATTEMPTS);
        return properties;
    }
}
