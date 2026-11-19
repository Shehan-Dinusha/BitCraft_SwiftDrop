package com.swiftdrop.dispatch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.core.QueueBuilder;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "swiftdrop.events.exchange";
    public static final String DEAD_LETTER_EXCHANGE_NAME = "swiftdrop.dlx.exchange";
    public static final String DISPATCH_DEAD_LETTER_ROUTING_KEY = "dlq.dispatch.events";
    public static final int DISPATCH_QUEUE_TTL_MILLIS = 86_400_000;
    public static final String DISPATCH_DEAD_LETTER_QUEUE = "dlq.dispatch.events.queue";
    /** Must match infra/local/rabbitmq/definitions.json exactly to avoid PRECONDITION_FAILED. */
    public static final int DISPATCH_DEAD_LETTER_TTL_MILLIS = 604_800_000;

    public static final String ORDER_CREATED_QUEUE = "dispatch.order-created.queue";
    public static final String ORDER_CANCELLED_QUEUE = "dispatch.order-cancelled.queue";
    public static final String PARCEL_STATUS_QUEUE = "dispatch.parcel-status.queue";

    public static final String ROUTING_KEY_ORDER_CREATED = "order.created";
    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";
    public static final String ROUTING_KEY_PARCEL_STATUS_CHANGED = "parcel.status.changed.#";

    public static final String ROUTING_KEY_COURIER_ASSIGNED = "dispatch.courier.assigned";
    public static final String ROUTING_KEY_DISPATCH_FAILED = "dispatch.failed";
    public static final String ROUTING_KEY_PARCEL_STATUS = "parcel.status.changed";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return dispatchQueue(ORDER_CREATED_QUEUE);
    }

    @Bean
    public Binding orderCreatedBinding(
            Queue orderCreatedQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderCreatedQueue)
                .to(eventsExchange)
                .with(ROUTING_KEY_ORDER_CREATED);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return dispatchQueue(ORDER_CANCELLED_QUEUE);
    }

    @Bean
    public Binding orderCancelledBinding(
            Queue orderCancelledQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderCancelledQueue)
                .to(eventsExchange)
                .with(ROUTING_KEY_ORDER_CANCELLED);
    }

    @Bean
    public Queue parcelStatusQueue() {
        return dispatchQueue(PARCEL_STATUS_QUEUE);
    }

    @Bean
    public Binding parcelStatusBinding(
            Queue parcelStatusQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(parcelStatusQueue)
                .to(eventsExchange)
                .with(ROUTING_KEY_PARCEL_STATUS_CHANGED);
    }

    /**
     * Dead-letter queue for every dispatch queue. Declared here, as the notification,
     * webhook and analytics services do for theirs, because brokers that do not
     * import definitions.json (AKS) would otherwise drop dead-lettered messages.
     * Durable quorum queue with a 7-day TTL and no dead-letter arguments of its own.
     */
    @Bean
    public Queue dispatchDeadLetterQueue() {
        return QueueBuilder.durable(DISPATCH_DEAD_LETTER_QUEUE)
                .quorum()
                .ttl(DISPATCH_DEAD_LETTER_TTL_MILLIS)
                .build();
    }

    @Bean
    public Binding dispatchDeadLetterBinding(
            Queue dispatchDeadLetterQueue,
            @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(dispatchDeadLetterQueue)
                .to(deadLetterExchange)
                .with(DISPATCH_DEAD_LETTER_ROUTING_KEY);
    }

    private Queue dispatchQueue(String queueName) {
        return QueueBuilder.durable(queueName)
                .quorum()
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", DISPATCH_DEAD_LETTER_ROUTING_KEY)
                .withArgument("x-message-ttl", DISPATCH_QUEUE_TTL_MILLIS)
                .build();
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
