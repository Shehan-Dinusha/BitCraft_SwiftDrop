package com.swiftdrop.order.config;

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
    public static final String FEE_REVISION_QUEUE = "customer-order.fee-revision.queue";
    public static final String FEE_REVISED_ROUTING_KEY = "parcel.fee.revised";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public Queue feeRevisionQueue() {
        return new Queue(FEE_REVISION_QUEUE, true);
    }

    @Bean
    public Binding feeRevisionBinding(Queue feeRevisionQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(feeRevisionQueue)
                .to(eventsExchange)
                .with(FEE_REVISED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
