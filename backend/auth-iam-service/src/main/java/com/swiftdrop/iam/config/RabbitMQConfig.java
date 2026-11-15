package com.swiftdrop.iam.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "swiftdrop.events.exchange";
    public static final String AUTH_QUEUE = "swiftdrop.auth.events";
    public static final String AUTH_ROUTING_PATTERN = "auth.#";

    /**
     * JSON (de)serialisation for every Rabbit template and listener in this service. Boot wires a
     * {@link MessageConverter} bean into its auto-configured RabbitTemplate and listener factories.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

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

    // ── Merchant approval events ──────────────────────────────────────────────

    public static final String MERCHANT_EVENTS_QUEUE = "swiftdrop.iam.merchant.events";
    public static final String MERCHANT_APPROVED_ROUTING_KEY = "merchant.approved";

    @Bean
    public Queue merchantEventsQueue() {
        return QueueBuilder.durable(MERCHANT_EVENTS_QUEUE)
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    @Bean
    public Binding merchantEventsBinding(Queue merchantEventsQueue, TopicExchange swiftdropExchange) {
        return BindingBuilder.bind(merchantEventsQueue)
                .to(swiftdropExchange)
                .with(MERCHANT_APPROVED_ROUTING_KEY);
    }
}
