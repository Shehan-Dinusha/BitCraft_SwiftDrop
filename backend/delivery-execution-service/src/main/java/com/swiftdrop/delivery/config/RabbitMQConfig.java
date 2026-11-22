package com.swiftdrop.delivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by the delivery service.
 *
 * <p>Every queue and its arguments must match {@code infra/local/rabbitmq/definitions.json}
 * exactly: the Docker Compose broker imports that file before the service starts, and RabbitMQ
 * rejects a declaration with different arguments ({@code PRECONDITION_FAILED}). The AKS broker
 * does not import the file, so these declarations are what create the queues there.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "swiftdrop.events.exchange";
    public static final String COURIER_ASSIGNED_QUEUE = "delivery.dispatch-assigned.queue";
    public static final String ROUTING_KEY_COURIER_ASSIGNED = "dispatch.courier.assigned";
    public static final String ORDER_CANCELLED_QUEUE = "delivery.order-cancelled.queue";
    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";
    public static final int COURIER_ASSIGNED_QUEUE_TTL_MILLIS = 86_400_000;

    public static final String DEAD_LETTER_EXCHANGE_NAME = "swiftdrop.dlx.exchange";
    public static final String DELIVERY_DEAD_LETTER_ROUTING_KEY = "dlq.delivery.events";
    public static final String DELIVERY_DEAD_LETTER_QUEUE = "dlq.delivery.events.queue";
    public static final int DELIVERY_DEAD_LETTER_TTL_MILLIS = 604_800_000;

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE_NAME, true, false);
    }

    /**
     * Courier assignments from dispatch. A message the consumer rejects (after the bounded retry in
     * {@link ListenerRetryPolicyConfig}) is dead-lettered to {@link #DELIVERY_DEAD_LETTER_QUEUE}
     * instead of being lost.
     */
    @Bean
    public Queue courierAssignedQueue() {
        return QueueBuilder.durable(COURIER_ASSIGNED_QUEUE)
                .quorum()
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", DELIVERY_DEAD_LETTER_ROUTING_KEY)
                .withArgument("x-message-ttl", COURIER_ASSIGNED_QUEUE_TTL_MILLIS)
                .build();
    }

    @Bean
    public Binding courierAssignedBinding(
            Queue courierAssignedQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(courierAssignedQueue)
                .to(eventsExchange)
                .with(ROUTING_KEY_COURIER_ASSIGNED);
    }

    /** Cancelled orders, so the courier's job is closed. Rejected messages are dead-lettered like assignments. */
    @Bean
    public Queue orderCancelledQueue() {
        return QueueBuilder.durable(ORDER_CANCELLED_QUEUE)
                .quorum()
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", DELIVERY_DEAD_LETTER_ROUTING_KEY)
                .withArgument("x-message-ttl", COURIER_ASSIGNED_QUEUE_TTL_MILLIS)
                .build();
    }

    @Bean
    public Binding orderCancelledBinding(
            Queue orderCancelledQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderCancelledQueue)
                .to(eventsExchange)
                .with(ROUTING_KEY_ORDER_CANCELLED);
    }

    /** Holds failed delivery messages for 7 days for inspection and replay. */
    @Bean
    public Queue deliveryDeadLetterQueue() {
        return QueueBuilder.durable(DELIVERY_DEAD_LETTER_QUEUE)
                .quorum()
                .ttl(DELIVERY_DEAD_LETTER_TTL_MILLIS)
                .build();
    }

    @Bean
    public Binding deliveryDeadLetterBinding(
            Queue deliveryDeadLetterQueue,
            @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deliveryDeadLetterQueue)
                .to(deadLetterExchange)
                .with(DELIVERY_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
