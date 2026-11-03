package com.swiftdrop.order.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

    @Test
    @DisplayName("The status-sync listener factory retries without requeue and the consumer is wired to it")
    void statusSyncListenerFactory() throws Exception {
        SimpleRabbitListenerContainerFactory factory = config.statusSyncListenerContainerFactory(
                mock(SimpleRabbitListenerContainerFactoryConfigurer.class), new CachingConnectionFactory());

        assertThat(RabbitMQConfig.STATUS_SYNC_LISTENER_FACTORY).isEqualTo("statusSyncListenerContainerFactory");
        assertThat(ReflectionTestUtils.getField(factory, "defaultRequeueRejected")).isEqualTo(false);
        assertThat(factory.getAdviceChain()).hasSize(1);
        assertThat(RabbitMQConfig.class.getMethod("statusSyncListenerContainerFactory",
                SimpleRabbitListenerContainerFactoryConfigurer.class,
                org.springframework.amqp.rabbit.connection.ConnectionFactory.class)
                .getAnnotation(org.springframework.context.annotation.Bean.class).name())
                .containsExactly("statusSyncListenerContainerFactory");

        var listener = com.swiftdrop.order.messaging.consumer.OrderStatusSyncConsumer.class.getMethods();
        var annotated = java.util.Arrays.stream(listener)
                .filter(m -> m.getName().equals("onParcelStatusChanged")).findFirst().orElseThrow()
                .getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class);
        assertThat(annotated.containerFactory()).isEqualTo(RabbitMQConfig.STATUS_SYNC_LISTENER_FACTORY);
    }

    @Test
    @DisplayName("The fee-revision listener keeps the default container factory")
    void feeRevisionListenerUsesDefaultFactory() {
        var annotated = java.util.Arrays.stream(
                        com.swiftdrop.order.messaging.consumer.DeliveryStatusSyncConsumer.class.getMethods())
                .filter(m -> m.getName().equals("onParcelFeeRevised")).findFirst().orElseThrow()
                .getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class);
        assertThat(annotated.containerFactory()).isEmpty();
    }
}
