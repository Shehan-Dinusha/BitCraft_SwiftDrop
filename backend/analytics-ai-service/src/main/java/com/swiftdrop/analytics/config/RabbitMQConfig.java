package com.swiftdrop.analytics.config;

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

    public static final String ANALYTICS_EVENTS_QUEUE = "analytics.events.queue";
    public static final String DLQ_ANALYTICS_EVENTS_QUEUE = "dlq.analytics.events.queue";
    public static final String DLQ_ROUTING_KEY = "dlq.analytics.events";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue analyticsEventsQueue() {
        return QueueBuilder.durable(ANALYTICS_EVENTS_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue dlqAnalyticsEventsQueue() {
        return QueueBuilder.durable(DLQ_ANALYTICS_EVENTS_QUEUE).build();
    }

    @Bean
    public Binding bindAnalyticsEventsParcel(Queue analyticsEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(analyticsEventsQueue).to(eventsExchange).with("parcel.status.changed.#");
    }

    @Bean
    public Binding bindAnalyticsEventsDispatch(Queue analyticsEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(analyticsEventsQueue).to(eventsExchange).with("dispatch.#");
    }

    @Bean
    public Binding bindDlqAnalyticsEvents(Queue dlqAnalyticsEventsQueue, TopicExchange dlxExchange) {
        return BindingBuilder.bind(dlqAnalyticsEventsQueue).to(dlxExchange).with(DLQ_ROUTING_KEY);
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
