package com.swiftdrop.webhook.config;

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

    public static final String WEBHOOK_EVENTS_QUEUE = "webhook.events.queue";
    public static final String DLQ_WEBHOOK_EVENTS_QUEUE = "dlq.webhook.events.queue";
    public static final String DLQ_ROUTING_KEY = "dlq.webhook.events";

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
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue dlqWebhookEventsQueue() {
        return QueueBuilder.durable(DLQ_WEBHOOK_EVENTS_QUEUE).build();
    }

    @Bean
    public Binding bindWebhookEventsParcel(Queue webhookEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(webhookEventsQueue).to(eventsExchange).with("parcel.status.changed.#");
    }

    @Bean
    public Binding bindWebhookEventsOrder(Queue webhookEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(webhookEventsQueue).to(eventsExchange).with("order.created");
    }

    @Bean
    public Binding bindWebhookEventsDispatch(Queue webhookEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(webhookEventsQueue).to(eventsExchange).with("webhook.dispatch.requested");
    }

    @Bean
    public Binding bindDlqWebhookEvents(Queue dlqWebhookEventsQueue, TopicExchange dlxExchange) {
        return BindingBuilder.bind(dlqWebhookEventsQueue).to(dlxExchange).with(DLQ_ROUTING_KEY);
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
