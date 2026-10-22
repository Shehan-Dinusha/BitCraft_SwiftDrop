package com.swiftdrop.dispatch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;

@Configuration
public class RabbitMQConfig {
    @Bean
    public TopicExchange eventsExchange() { return new TopicExchange("swiftdrop.events"); }

}
