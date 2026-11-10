package com.swiftdrop.analytics.config;

import org.springframework.amqp.core.*;
import com.swiftdrop.contracts.events.EventRoutingKeys;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "swiftdrop.events.exchange";
    public static final String DLX_EXCHANGE = "swiftdrop.dlx.exchange";

    public static final String ANALYTICS_EVENTS_QUEUE = "analytics.events.queue";
    public static final String DLQ_ANALYTICS_EVENTS_QUEUE = "dlq.analytics.events.queue";
    public static final String DLQ_ROUTING_KEY = "dlq.analytics.events";
    public static final String ANALYTICS_LISTENER_FACTORY = "analyticsListenerContainerFactory";

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
                .quorum()
                .ttl(86_400_000)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue dlqAnalyticsEventsQueue() {
        return QueueBuilder.durable(DLQ_ANALYTICS_EVENTS_QUEUE).quorum().ttl(604_800_000).build();
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
    public Binding bindAnalyticsOrderCreated(Queue analyticsEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(analyticsEventsQueue).to(eventsExchange).with(EventRoutingKeys.ORDER_CREATED);
    }

    @Bean(name = ANALYTICS_LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory analyticsListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // Validate bytes in the listener, never a producer-supplied __TypeId__.
        factory.setMessageConverter(new SimpleMessageConverter() {
            @Override
            public Object fromMessage(Message message) {
                // SimpleMessageConverter normally deserializes Java-serialized
                // content. Always return bytes, regardless of content-type.
                return message.getBody();
            }
        });
        factory.setDefaultRequeueRejected(false);
        var policy = new SimpleRetryPolicy(3,
                Map.of(AmqpRejectAndDontRequeueException.class, false), true, true);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(policy)
                .backOffOptions(1_000, 2.0, 10_000)
                // Default recoverers log Message.toString(), which can include
                // a rejected body. Reject with a safe message instead.
                .recoverer((message, cause) -> {
                    throw new AmqpRejectAndDontRequeueException(
                            "Analytics event rejected after validation or bounded processing attempts");
                })
                .build());
        return factory;
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
