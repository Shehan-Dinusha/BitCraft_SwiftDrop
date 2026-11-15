package com.swiftdrop.dispatch;

import com.swiftdrop.dispatch.config.RabbitMQConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The dispatch RabbitMQ topology must declare cleanly on a broker that has already
 * imported the shared infra/local/rabbitmq/definitions.json, exactly as the local
 * Docker Compose broker does. A queue argument that differs from the definition
 * would be rejected by RabbitMQ with PRECONDITION_FAILED.
 */
@Testcontainers(disabledWithoutDocker = true)
class DispatchDeadLetterTopologyIT {

    /** Read-only use of the shared broker definitions; the module directory is the working directory. */
    private static final Path DEFINITIONS = Path.of("..", "..", "infra", "local", "rabbitmq", "definitions.json");

    @Container
    static RabbitMQContainer broker = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withCopyFileToContainer(MountableFile.forHostPath(DEFINITIONS), "/etc/rabbitmq/definitions.json")
            .withCopyToContainer(Transferable.of("management.load_definitions = /etc/rabbitmq/definitions.json\n"),
                    "/etc/rabbitmq/conf.d/20-swiftdrop-definitions.conf");

    private final RabbitMQConfig config = new RabbitMQConfig();
    private CachingConnectionFactory connectionFactory;
    private RabbitAdmin admin;

    @BeforeEach
    void connectWithTheDefinitionsUser() {
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
    @DisplayName("Dispatch topology declares on a broker preloaded with definitions.json without PRECONDITION_FAILED")
    void dispatchTopologyIsCompatibleWithBrokerDefinitions() {
        Properties preloaded = admin.getQueueProperties(RabbitMQConfig.DISPATCH_DEAD_LETTER_QUEUE);
        assertThat(preloaded).as("DLQ imported from definitions.json before the service declares it").isNotNull();

        // Each declaration throws if RabbitMQ answers PRECONDITION_FAILED.
        admin.declareExchange(config.eventsExchange());
        admin.declareExchange(config.deadLetterExchange());
        admin.declareQueue(config.dispatchDeadLetterQueue());
        admin.declareBinding(config.dispatchDeadLetterBinding(config.dispatchDeadLetterQueue(), config.deadLetterExchange()));
        for (Queue queue : List.of(config.orderCreatedQueue(), config.orderCancelledQueue(), config.parcelStatusQueue())) {
            admin.declareQueue(queue);
        }

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.convertAndSend(RabbitMQConfig.DEAD_LETTER_EXCHANGE_NAME,
                RabbitMQConfig.DISPATCH_DEAD_LETTER_ROUTING_KEY, "dead-letter probe");
        Message routed = template.receive(RabbitMQConfig.DISPATCH_DEAD_LETTER_QUEUE, 5_000);
        assertThat(routed).as("dlq.dispatch.events routing key reaches dlq.dispatch.events.queue").isNotNull();
    }

    @Test
    @DisplayName("Control: a DLQ declaration with a different TTL is rejected by the broker")
    void mismatchedDeclarationIsRejected() {
        Queue mismatched = QueueBuilder.durable(RabbitMQConfig.DISPATCH_DEAD_LETTER_QUEUE)
                .quorum()
                .ttl(86_400_000)
                .build();

        assertThatThrownBy(() -> admin.declareQueue(mismatched))
                .hasStackTraceContaining("PRECONDITION_FAILED");
    }
}
