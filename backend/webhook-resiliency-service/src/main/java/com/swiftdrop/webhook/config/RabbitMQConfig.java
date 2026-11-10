package com.swiftdrop.webhook.config;

import com.swiftdrop.contracts.events.EventRoutingKeys;
import com.swiftdrop.webhook.exception.NonRetryableIngestionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

/**
 * Broker topology and listener behaviour for event ingestion.
 *
 * <p>Queue declarations must match {@code infra/local/rabbitmq/definitions.json} argument for argument
 * (quorum type, TTL, dead-letter exchange and key). RabbitMQ refuses to re-declare an existing queue with
 * different arguments ({@code PRECONDITION_FAILED}).</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(IngestionRetryProperties.class)
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "swiftdrop.events.exchange";
    public static final String DLX_EXCHANGE = "swiftdrop.dlx.exchange";

    public static final String WEBHOOK_EVENTS_QUEUE = "webhook.events.queue";
    public static final String DLQ_WEBHOOK_EVENTS_QUEUE = "dlq.webhook.events.queue";
    public static final String DLQ_ROUTING_KEY = "dlq.webhook.events";
    public static final String INGESTION_LISTENER_FACTORY = "webhookIngestionListenerContainerFactory";

    /** Undelivered events are dead-lettered after a day rather than kept indefinitely. */
    static final int EVENTS_QUEUE_TTL_MS = 86_400_000;
    /** Dead letters are kept for a week for diagnosis and replay. */
    static final int DLQ_TTL_MS = 604_800_000;

    /** Bound for parity with the broker definitions; it has no producer and the consumer rejects it. */
    static final String WEBHOOK_DISPATCH_REQUESTED = "webhook.dispatch.requested";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue webhookEventsQueue() {
        return QueueBuilder.durable(WEBHOOK_EVENTS_QUEUE)
                .quorum()
                .ttl(EVENTS_QUEUE_TTL_MS)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue dlqWebhookEventsQueue() {
        return QueueBuilder.durable(DLQ_WEBHOOK_EVENTS_QUEUE).quorum().ttl(DLQ_TTL_MS).build();
    }

    @Bean
    public Binding bindWebhookEventsParcel(Queue webhookEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(webhookEventsQueue).to(eventsExchange)
                .with(EventRoutingKeys.PARCEL_STATUS_CHANGED + ".#");
    }

    @Bean
    public Binding bindWebhookEventsOrder(Queue webhookEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(webhookEventsQueue).to(eventsExchange).with(EventRoutingKeys.ORDER_CREATED);
    }

    @Bean
    public Binding bindWebhookEventsDispatch(Queue webhookEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(webhookEventsQueue).to(eventsExchange).with(WEBHOOK_DISPATCH_REQUESTED);
    }

    @Bean
    public Binding bindDlqWebhookEvents(Queue dlqWebhookEventsQueue, TopicExchange dlxExchange) {
        return BindingBuilder.bind(dlqWebhookEventsQueue).to(dlxExchange).with(DLQ_ROUTING_KEY);
    }

    /**
     * Listener container for ingestion. Prefetch and consumer concurrency come from
     * {@code spring.rabbitmq.listener.simple.*}; the settings below are correctness requirements, not tuning.
     *
     * <ul>
     *   <li>{@link AcknowledgeMode#AUTO}: the container acknowledges only after the listener returns, i.e. after
     *       the ingestion transaction committed. The container is deliberately not channel-transacted and has no
     *       transaction manager, so acknowledgement can never precede the database commit.</li>
     *   <li>Failures are retried in-process a bounded number of times (transient database errors), then
     *       rejected without requeue so the broker dead-letters them. Invalid events are rejected immediately.</li>
     *   <li>The quorum queue's {@code delivery-limit} policy bounds redeliveries caused by consumer crashes.</li>
     * </ul>
     */
    @Bean(name = INGESTION_LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory webhookIngestionListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            IngestionRetryProperties retry) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setChannelTransacted(false);
        factory.setDefaultRequeueRejected(false);
        // Hand the listener raw bytes; never let a producer-supplied __TypeId__ choose a class to instantiate.
        factory.setMessageConverter(new SimpleMessageConverter() {
            @Override
            public Object fromMessage(Message message) {
                return message.getBody();
            }
        });
        var policy = new SimpleRetryPolicy(retry.maxAttempts(), Map.of(
                NonRetryableIngestionException.class, false,
                AmqpRejectAndDontRequeueException.class, false), true, true);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(policy)
                .backOffOptions(retry.initialInterval().toMillis(), retry.multiplier(), retry.maxInterval().toMillis())
                .recoverer(deadLetteringRecoverer())
                .build());
        return factory;
    }

    /**
     * Rejects to the dead-letter queue, where the original message (body and headers) is kept for diagnosis
     * and replay. Logs identifiers and the failure type only: Spring's default recoverer would log the body.
     */
    private static MessageRecoverer deadLetteringRecoverer() {
        return (message, cause) -> {
            Throwable reason = rootCause(cause);
            String detail = reason instanceof NonRetryableIngestionException ? reason.getMessage()
                    : reason.getClass().getSimpleName();
            log.warn("Dead-lettering inbound message routingKey={} messageId={} reason={}",
                    message.getMessageProperties().getReceivedRoutingKey(),
                    message.getMessageProperties().getMessageId(), detail);
            throw new AmqpRejectAndDontRequeueException("Inbound webhook event rejected: " + detail);
        };
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && !(current instanceof NonRetryableIngestionException)) {
            current = current.getCause();
        }
        return current;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
