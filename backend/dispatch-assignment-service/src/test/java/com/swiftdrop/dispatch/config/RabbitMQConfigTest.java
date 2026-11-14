package com.swiftdrop.dispatch.config;

import org.junit.jupiter.api.Test;
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
}
