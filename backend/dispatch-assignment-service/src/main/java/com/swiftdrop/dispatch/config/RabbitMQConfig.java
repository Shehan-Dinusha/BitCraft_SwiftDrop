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

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "swiftdrop.events.exchange";

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
    public Queue orderCreatedQueue() {
        return new Queue(ORDER_CREATED_QUEUE, true);
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderCreatedQueue)
                .to(eventsExchange)
                .with(ROUTING_KEY_ORDER_CREATED);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(ORDER_CANCELLED_QUEUE, true);
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(orderCancelledQueue)
                .to(eventsExchange)
                .with(ROUTING_KEY_ORDER_CANCELLED);
    }

    @Bean
    public Queue parcelStatusQueue() {
        return new Queue(PARCEL_STATUS_QUEUE, true);
    }

    @Bean
    public Binding parcelStatusBinding(Queue parcelStatusQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(parcelStatusQueue)
                .to(eventsExchange)
                .with(ROUTING_KEY_PARCEL_STATUS_CHANGED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
