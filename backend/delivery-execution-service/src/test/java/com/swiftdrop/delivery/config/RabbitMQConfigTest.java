package com.swiftdrop.delivery.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    /** Shared broker definitions; the module directory is the working directory. */
    private static final Path DEFINITIONS = Path.of("..", "..", "infra", "local", "rabbitmq", "definitions.json");

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void courierAssignedQueueDeadLettersToTheDeliveryDlq() {
        Queue queue = config.courierAssignedQueue();

        assertThat(queue.getName()).isEqualTo("delivery.dispatch-assigned.queue");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isFalse();
        assertThat(queue.getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-dead-letter-exchange", "swiftdrop.dlx.exchange")
                .containsEntry("x-dead-letter-routing-key", "dlq.delivery.events");
    }

    @Test
    void declaresTheDeadLetterExchange() {
        assertThat(config.deadLetterExchange().getName()).isEqualTo("swiftdrop.dlx.exchange");
        assertThat(config.deadLetterExchange().isDurable()).isTrue();
        assertThat(config.deadLetterExchange().isAutoDelete()).isFalse();
    }

    @Test
    void bindsTheDlqWithTheRoutingKeyTheQueueDeadLettersWith() {
        Binding binding = config.deliveryDeadLetterBinding(config.deliveryDeadLetterQueue(), config.deadLetterExchange());

        assertThat(binding.getExchange()).isEqualTo("swiftdrop.dlx.exchange");
        assertThat(binding.getDestination()).isEqualTo("dlq.delivery.events.queue");
        assertThat(binding.getDestinationType()).isEqualTo(Binding.DestinationType.QUEUE);
        assertThat(config.courierAssignedQueue().getArguments())
                .containsEntry("x-dead-letter-routing-key", binding.getRoutingKey());
    }

    @Test
    void courierAssignedBindingUsesTheDispatchRoutingKey() {
        Binding binding = config.courierAssignedBinding(config.courierAssignedQueue(), config.eventsExchange());

        assertThat(binding.getExchange()).isEqualTo("swiftdrop.events.exchange");
        assertThat(binding.getRoutingKey()).isEqualTo("dispatch.courier.assigned");
    }

    /**
     * The Docker Compose broker imports definitions.json before the service declares its queues.
     * Any argument that differs makes RabbitMQ reject the declaration with PRECONDITION_FAILED.
     */
    @Test
    void queuesMatchTheSharedBrokerDefinitionsExactly() throws IOException {
        for (Queue queue : new Queue[] {config.courierAssignedQueue(), config.orderCancelledQueue(),
                config.deliveryDeadLetterQueue()}) {
            assertThat(queue.getArguments())
                    .as(queue.getName())
                    .containsExactlyInAnyOrderEntriesOf(definitionArguments(queue.getName()));
        }
    }

    private static Map<String, Object> definitionArguments(String queueName) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        for (JsonNode queue : mapper.readTree(DEFINITIONS.toFile()).path("queues")) {
            if (queueName.equals(queue.path("name").asText())) {
                return mapper.convertValue(queue.path("arguments"), new TypeReference<>() { });
            }
        }
        throw new AssertionError(queueName + " is missing from " + DEFINITIONS);
    }
}
