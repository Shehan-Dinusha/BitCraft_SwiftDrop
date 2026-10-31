package com.swiftdrop.delivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "swiftdrop.events.exchange";
    public static final String COURIER_ASSIGNED_QUEUE = "delivery.dispatch-assigned.queue";
    public static final String ROUTING_KEY_COURIER_ASSIGNED = "dispatch.courier.assigned";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue courierAssignedQueue() {
        return new Queue(COURIER_ASSIGNED_QUEUE, true);
    }

    @Bean
    public Binding courierAssignedBinding(Queue courierAssignedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(courierAssignedQueue)
                .to(eventsExchange)
                .with(ROUTING_KEY_COURIER_ASSIGNED);
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
