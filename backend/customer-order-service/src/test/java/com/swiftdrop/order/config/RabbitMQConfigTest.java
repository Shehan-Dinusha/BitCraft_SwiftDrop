package com.swiftdrop.order.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    @DisplayName("The status-sync queue is durable and has the dead-letter, TTL and quorum arguments of its sibling queues")
    void statusSyncQueueArguments() {
        Queue queue = config.statusSyncQueue();

        assertThat(queue.getName()).isEqualTo("customer-order.status-sync.queue");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "swiftdrop.dlx.exchange")
                .containsEntry("x-dead-letter-routing-key", "dlq.order.events")
                .containsEntry("x-message-ttl", 86_400_000L)
                .containsEntry("x-queue-type", "quorum");
    }

    @Test
    @DisplayName("The status-sync queue is bound to the shared exchange with parcel.status.changed.#")
    void statusSyncBinding() {
        TopicExchange exchange = config.eventsExchange();

        Binding binding = config.statusSyncBinding(config.statusSyncQueue(), exchange);

        assertThat(binding.getExchange()).isEqualTo("swiftdrop.events.exchange");
        assertThat(binding.getDestination()).isEqualTo("customer-order.status-sync.queue");
        assertThat(binding.getRoutingKey()).isEqualTo("parcel.status.changed.#");
    }

    @Test
    @DisplayName("The existing fee-revision queue and binding are unchanged")
    void feeRevisionBindingUnchanged() {
        Binding binding = config.feeRevisionBinding(config.feeRevisionQueue(), config.eventsExchange());

        assertThat(binding.getDestination()).isEqualTo("customer-order.fee-revision.queue");
        assertThat(binding.getRoutingKey()).isEqualTo("parcel.fee.revised");
        assertThat(config.feeRevisionQueue().getArguments()).isEmpty();
    }
}
