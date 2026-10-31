package com.swiftdrop.iam.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "swiftdrop.events.exchange";
    public static final String AUTH_QUEUE = "swiftdrop.auth.events";
    public static final String AUTH_ROUTING_PATTERN = "auth.#";

    @Bean
    public TopicExchange swiftdropExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue authQueue() {
        return QueueBuilder.durable(AUTH_QUEUE)
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    @Bean
    public Binding authBinding(Queue authQueue, TopicExchange swiftdropExchange) {
        return BindingBuilder.bind(authQueue).to(swiftdropExchange).with(AUTH_ROUTING_PATTERN);
    }
}
