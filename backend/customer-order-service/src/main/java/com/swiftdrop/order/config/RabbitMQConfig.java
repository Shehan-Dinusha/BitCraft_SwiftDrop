package com.swiftdrop.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

    /** Receives every parcel status change so the order keeps following delivery progress. */
    public static final String STATUS_SYNC_QUEUE = "customer-order.status-sync.queue";
    /** Also matches the bare key "parcel.status.changed" (# matches zero or more words). */
    public static final String PARCEL_STATUS_BINDING_KEY = "parcel.status.changed.#";
    public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.dlx.exchange";
    public static final String DEAD_LETTER_ROUTING_KEY = "dlq.order.events";
    private static final long STATUS_SYNC_QUEUE_TTL_MS = 86_400_000L;

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
    public Queue statusSyncQueue() {
        // Same dead-letter, TTL and quorum arguments as the sibling queues in infra/local/rabbitmq/definitions.json.
        return QueueBuilder.durable(STATUS_SYNC_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .withArgument("x-message-ttl", STATUS_SYNC_QUEUE_TTL_MS)
                .quorum()
                .build();
    }

    @Bean
    public Binding statusSyncBinding(Queue statusSyncQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(statusSyncQueue)
                .to(eventsExchange)
                .with(PARCEL_STATUS_BINDING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
