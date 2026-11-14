package com.swiftdrop.dispatch;

import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import com.swiftdrop.dispatch.domain.repository.CourierShiftRepository;
import com.swiftdrop.dispatch.domain.repository.DispatchAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = DispatchAssignmentApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class OrderCreatedConsumerIT {

    private static final String DISPATCH_DLQ = "dlq.dispatch.events.queue";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Autowired
    private DispatchAssignmentRepository assignmentRepository;

    @Autowired
    private CourierShiftRepository courierShiftRepository;

    @BeforeEach
    void resetBrokerAndForceUnassignedPath() {
        startListeners();
        stopListeners();
        rabbitAdmin.purgeQueue(RabbitMQConfig.ORDER_CREATED_QUEUE, true);
        declareInfrastructureDeadLetterTopology();
        rabbitAdmin.purgeQueue(DISPATCH_DLQ, true);

        List<CourierShift> couriers = courierShiftRepository.findAll();
        couriers.forEach(courier -> courier.setShiftStatus(ShiftStatus.OFFLINE));
        courierShiftRepository.saveAllAndFlush(couriers);
    }

    @Test
    void validOrderCreatedMessageCreatesOneUnassignedDispatchRecord() {
        OrderCreatedEvent event = newEvent();

        publish(event);

        assertQueueContainsOneMessage();
        startListeners();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<DispatchAssignment> records = recordsFor(event.orderId());
            assertThat(records).hasSize(1);
            assertThat(records.getFirst().getStatus()).isEqualTo(AssignmentStatus.UNASSIGNED);
        });
    }

    @Test
    void duplicateOrderCreatedMessageDoesNotCreateAnotherRecord() {
        OrderCreatedEvent event = newEvent();

        publish(event);
        assertQueueContainsOneMessage();
        startListeners();
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(recordsFor(event.orderId())).hasSize(1));

        stopListeners();
        publish(event);
        publish(event);
        assertQueueContainsMessageCount(2);
        startListeners();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(recordsFor(event.orderId())).hasSize(1);
            assertThat(queueInfo().getMessageCount()).isZero();
        });
    }

    @Test
    void malformedOrderCreatedMessageIsRejectedAndRoutedToTheDispatchDlq() {
        stopListeners();

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_ORDER_CREATED,
                new Message("{not-json".getBytes(StandardCharsets.UTF_8), properties)
        );
        assertQueueContainsOneMessage();

        startListeners();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(queueInfo().getMessageCount()).isZero();
            QueueInformation deadLetterQueue = rabbitAdmin.getQueueInfo(DISPATCH_DLQ);
            assertThat(deadLetterQueue).isNotNull();
            assertThat(deadLetterQueue.getMessageCount()).isEqualTo(1);
        });
    }

    private void declareInfrastructureDeadLetterTopology() {
        rabbitAdmin.declareExchange(new TopicExchange(RabbitMQConfig.DEAD_LETTER_EXCHANGE_NAME, true, false));
        Queue deadLetterQueue = QueueBuilder.durable(DISPATCH_DLQ)
                .quorum()
                .withArgument("x-message-ttl", 604_800_000)
                .build();
        rabbitAdmin.declareQueue(deadLetterQueue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(deadLetterQueue)
                .to(new TopicExchange(RabbitMQConfig.DEAD_LETTER_EXCHANGE_NAME, true, false))
                .with(RabbitMQConfig.DISPATCH_DEAD_LETTER_ROUTING_KEY));
    }

    private OrderCreatedEvent newEvent() {
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SD-COL-" + UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID(),
                UUID.randomUUID(),
                SriLankanDistrict.COLOMBO,
                SriLankanDistrict.GAMPAHA,
                OrderSpeed.STANDARD,
                null,
                null,
                null,
                "LKR",
                null
        );
    }

    private void publish(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY_ORDER_CREATED,
                event
        );
    }

    private List<DispatchAssignment> recordsFor(UUID orderId) {
        return assignmentRepository.findAll().stream()
                .filter(record -> orderId.equals(record.getOrderId()))
                .toList();
    }

    private QueueInformation queueInfo() {
        QueueInformation information = rabbitAdmin.getQueueInfo(RabbitMQConfig.ORDER_CREATED_QUEUE);
        assertThat(information).as("order.created queue must be declared").isNotNull();
        return information;
    }

    private void assertQueueContainsOneMessage() {
        assertQueueContainsMessageCount(1);
    }

    private void assertQueueContainsMessageCount(int expectedCount) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(queueInfo().getMessageCount()).isEqualTo(expectedCount));
    }

    private void stopListeners() {
        listenerRegistry.getListenerContainers().forEach(MessageListenerContainer::stop);
    }

    private void startListeners() {
        listenerRegistry.getListenerContainers().forEach(container -> {
            if (!container.isRunning()) {
                container.start();
            }
        });
        await().atMost(Duration.ofSeconds(10))
                .until(() -> listenerRegistry.getListenerContainers().stream()
                        .allMatch(MessageListenerContainer::isRunning));
    }
}
