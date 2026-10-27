package com.swiftdrop.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "swiftdrop.events.exchange";
    public static final String DLX_EXCHANGE = "swiftdrop.dlx.exchange";

    public static final String NOTIFICATION_EVENTS_QUEUE = "notification.events.queue";
    public static final String DLQ_NOTIFICATION_EVENTS_QUEUE = "dlq.notification.events.queue";
    public static final String DLQ_ROUTING_KEY = "dlq.notification.events";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationEventsQueue() {
        return QueueBuilder.durable(NOTIFICATION_EVENTS_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue dlqNotificationEventsQueue() {
        return QueueBuilder.durable(DLQ_NOTIFICATION_EVENTS_QUEUE).build();
    }

    @Bean
    public Binding bindNotificationEventsParcel(Queue notificationEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(eventsExchange).with("parcel.status.changed.#");
    }

    @Bean
    public Binding bindNotificationEventsNotification(Queue notificationEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(eventsExchange).with("notification.#");
    }

    @Bean
    public Binding bindDlqNotificationEvents(Queue dlqNotificationEventsQueue, TopicExchange dlxExchange) {
        return BindingBuilder.bind(dlqNotificationEventsQueue).to(dlxExchange).with(DLQ_ROUTING_KEY);
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
