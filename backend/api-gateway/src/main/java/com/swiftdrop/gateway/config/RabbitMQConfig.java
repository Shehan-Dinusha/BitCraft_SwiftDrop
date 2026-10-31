package com.swiftdrop.gateway.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String EVENTS_EXCHANGE = "swiftdrop.events.exchange";
    public static final String TRACKING_QUEUE = "swiftdrop.gateway.tracking.queue";
    public static final String FEE_QUEUE = "swiftdrop.gateway.fee.queue";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue trackingQueue() {
        return QueueBuilder.durable(TRACKING_QUEUE).quorum().build();
    }

    @Bean
    public Queue feeQueue() {
        return QueueBuilder.durable(FEE_QUEUE).quorum().build();
    }

    @Bean
    public Binding trackingBinding(Queue trackingQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(trackingQueue).to(eventsExchange).with("parcel.status.changed.#");
    }

    @Bean
    public Binding feeBinding(Queue feeQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(feeQueue).to(eventsExchange).with("parcel.fee.revised");
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
