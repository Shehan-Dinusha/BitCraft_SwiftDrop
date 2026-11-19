package com.swiftdrop.dispatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void dispatchQueuesDeclareInfrastructureDeadLetterArguments() {
        List<Queue> queues = List.of(
                config.orderCreatedQueue(),
                config.orderCancelledQueue(),
                config.parcelStatusQueue()
        );

        for (Queue queue : queues) {
            Map<String, Object> arguments = queue.getArguments();
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.isExclusive()).isFalse();
            assertThat(queue.isAutoDelete()).isFalse();
            assertThat(arguments)
                    .containsEntry("x-queue-type", "quorum")
                    .containsEntry("x-dead-letter-exchange", RabbitMQConfig.DEAD_LETTER_EXCHANGE_NAME)
                    .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.DISPATCH_DEAD_LETTER_ROUTING_KEY)
                    .containsEntry("x-message-ttl", RabbitMQConfig.DISPATCH_QUEUE_TTL_MILLIS);
        }
    }

    @Test
    void declaresTheInfrastructureDeadLetterExchange() {
        assertThat(config.deadLetterExchange().getName())
                .isEqualTo(RabbitMQConfig.DEAD_LETTER_EXCHANGE_NAME);
        assertThat(config.deadLetterExchange().isDurable()).isTrue();
        assertThat(config.deadLetterExchange().isAutoDelete()).isFalse();
    }

    @Test
    void declaresTheDispatchDeadLetterQueueExactlyAsDefinitionsJson() {
        Queue dlq = config.dispatchDeadLetterQueue();

        assertThat(dlq.getName()).isEqualTo("dlq.dispatch.events.queue");
        assertThat(dlq.isDurable()).isTrue();
        assertThat(dlq.isExclusive()).isFalse();
        assertThat(dlq.isAutoDelete()).isFalse();
        // Exactly these two arguments: any extra or different value would make the
        // broker reject the declaration with PRECONDITION_FAILED.
        assertThat(dlq.getArguments()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "x-queue-type", "quorum",
                "x-message-ttl", 604_800_000));
    }

    @Test
    void bindsTheDeadLetterQueueToTheDeadLetterExchangeWithTheDispatchRoutingKey() {
        Binding binding = config.dispatchDeadLetterBinding(
                config.dispatchDeadLetterQueue(), config.deadLetterExchange());

        assertThat(binding.getExchange()).isEqualTo("swiftdrop.dlx.exchange");
        assertThat(binding.getRoutingKey()).isEqualTo("dlq.dispatch.events");
        assertThat(binding.getDestination()).isEqualTo("dlq.dispatch.events.queue");
        assertThat(binding.getDestinationType()).isEqualTo(Binding.DestinationType.QUEUE);
        // The routing key every dispatch queue dead-letters with must reach this binding.
        assertThat(config.orderCreatedQueue().getArguments())
                .containsEntry("x-dead-letter-routing-key", binding.getRoutingKey());
    }
}
