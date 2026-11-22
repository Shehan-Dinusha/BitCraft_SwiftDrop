package com.swiftdrop.delivery;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import com.swiftdrop.delivery.config.RabbitMQConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The delivery RabbitMQ topology must declare cleanly on a broker preloaded with the shared
 * definitions.json (as the Docker Compose broker is), and a rejected courier assignment must end
 * up in the delivery dead-letter queue instead of being lost.
 */
@Testcontainers(disabledWithoutDocker = true)
class DeliveryDeadLetterTopologyIT {

    /** Read-only use of the shared broker definitions; the module directory is the working directory. */
    private static final Path DEFINITIONS = Path.of("..", "..", "infra", "local", "rabbitmq", "definitions.json");

    @Container
    // Closed by the Testcontainers JUnit extension (@Container), not by this class.
    @SuppressWarnings("resource")
    static RabbitMQContainer broker = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withCopyFileToContainer(MountableFile.forHostPath(DEFINITIONS), "/etc/rabbitmq/definitions.json")
            .withCopyToContainer(Transferable.of("management.load_definitions = /etc/rabbitmq/definitions.json\n"),
                    "/etc/rabbitmq/conf.d/20-swiftdrop-definitions.conf");

    private final RabbitMQConfig config = new RabbitMQConfig();
    private CachingConnectionFactory connectionFactory;
    private RabbitAdmin admin;

    @BeforeEach
    void connect() {
        assertThat(Files.isRegularFile(DEFINITIONS)).as("shared definitions.json is present").isTrue();
        connectionFactory = new CachingConnectionFactory(broker.getHost(), broker.getAmqpPort());
        // The broker user comes from definitions.json, matching the Docker Compose defaults.
        connectionFactory.setUsername("swiftdrop");
        connectionFactory.setPassword("changeme_rabbit");
        admin = new RabbitAdmin(connectionFactory);
    }

    @AfterEach
    void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("Delivery topology declares on a broker preloaded with definitions.json without PRECONDITION_FAILED")
    void topologyIsCompatibleWithBrokerDefinitions() {
        // Each declaration throws if RabbitMQ answers PRECONDITION_FAILED.
        admin.declareExchange(config.eventsExchange());
        admin.declareExchange(config.deadLetterExchange());
        admin.declareQueue(config.deliveryDeadLetterQueue());
        admin.declareBinding(config.deliveryDeadLetterBinding(config.deliveryDeadLetterQueue(), config.deadLetterExchange()));
        admin.declareQueue(config.courierAssignedQueue());
        admin.declareBinding(config.courierAssignedBinding(config.courierAssignedQueue(), config.eventsExchange()));
        admin.declareQueue(config.orderCancelledQueue());
        admin.declareBinding(config.orderCancelledBinding(config.orderCancelledQueue(), config.eventsExchange()));
    }

    @Test
    @DisplayName("A rejected courier assignment is dead-lettered to dlq.delivery.events.queue")
    void rejectedAssignmentReachesTheDeadLetterQueue() {
        topologyIsCompatibleWithBrokerDefinitions();
        admin.purgeQueue(RabbitMQConfig.DELIVERY_DEAD_LETTER_QUEUE);

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_COURIER_ASSIGNED,
                "courier assignment probe");

        // Reject without requeue, which is what the listener container does once retries give up.
        Boolean rejected = template.execute((Channel channel) -> {
            GetResponse response = channel.basicGet(RabbitMQConfig.COURIER_ASSIGNED_QUEUE, false);
            if (response == null) {
                return false;
            }
            channel.basicReject(response.getEnvelope().getDeliveryTag(), false);
            return true;
        });
        assertThat(rejected).as("probe reached delivery.dispatch-assigned.queue").isTrue();

        Message deadLettered = template.receive(RabbitMQConfig.DELIVERY_DEAD_LETTER_QUEUE, 10_000);
        assertThat(deadLettered).as("rejected message kept in the dead-letter queue").isNotNull();
        assertThat(new String(deadLettered.getBody())).contains("courier assignment probe");
    }
}
