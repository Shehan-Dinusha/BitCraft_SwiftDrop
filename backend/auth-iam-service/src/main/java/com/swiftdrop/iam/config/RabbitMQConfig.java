package com.swiftdrop.iam.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange swiftdropExchange() {
        return new TopicExchange("swiftdrop.events");
    }

    @Bean
    public Queue authQueue() {
        return new Queue("swiftdrop.auth.events", true);
    }

    @Bean
    public Binding authBinding() {
        return BindingBuilder.bind(authQueue()).to(swiftdropExchange()).with("auth.#");
    }
}
