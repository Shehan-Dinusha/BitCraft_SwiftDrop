package com.swiftdrop.dispatch;

import com.swiftdrop.contracts.enums.InternalQueueStatus;
import com.swiftdrop.contracts.enums.OrderSpeed;
import com.swiftdrop.contracts.enums.PublicOrderStatus;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.contracts.events.delivery.ParcelStatusChangedEvent;
import com.swiftdrop.contracts.events.order.OrderCreatedEvent;
import com.swiftdrop.dispatch.config.RabbitMQConfig;
import com.swiftdrop.dispatch.exception.InvalidAssignmentTransitionException;
import com.swiftdrop.dispatch.service.CourierMatchingEngine;
import com.swiftdrop.dispatch.service.DispatchAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Consumer failure handling against a real broker: bounded retry for transient
 * failures, immediate dead-lettering for permanent ones, and no redelivery loop.
 * The business services are mocked so each failure can be injected precisely;
 * the attempt limit and failure classification are the production configuration.
 */
@SpringBootTest(classes = DispatchAssignmentApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class ConsumerFailureHandlingIT {

    private static final String DISPATCH_DLQ = "dlq.dispatch.events.queue";
    private static final int CONFIGURED_MAX_ATTEMPTS = 3;

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
        // Shorter backoff keeps the test fast; max-attempts stays at the production value.
        registry.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "100ms");
        registry.add("spring.rabbitmq.listener.simple.retry.max-interval", () -> "200ms");
    }

    @MockitoBean
    private CourierMatchingEngine matchingEngine;

    @MockitoBean
    private DispatchAssignmentService dispatchAssignmentService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void resetQueues() {
        rabbitAdmin.purgeQueue(RabbitMQConfig.ORDER_CREATED_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitMQConfig.PARCEL_STATUS_QUEUE, false);
        // Fresh broker without definitions.json, like AKS: the DLQ and its binding
        // must come from the service's own RabbitMQConfig declaration.
        assertThat(rabbitAdmin.getQueueInfo(DISPATCH_DLQ)).as("DLQ declared by the dispatch service").isNotNull();
        rabbitAdmin.purgeQueue(DISPATCH_DLQ, false);
    }

    @Test
    @DisplayName("A transient failure is retried and the message is then processed and acknowledged")
    void transientFailureIsRetriedThenAcknowledged() {
        doThrow(new TransientDataAccessResourceException("database briefly unavailable"))
                .doReturn(null)
                .when(matchingEngine).processOrderCreated(any());

        publishOrderCreated(orderCreated(UUID.randomUUID()));

        verify(matchingEngine, after(2_000).times(2)).processOrderCreated(any());
        assertQueuesDrained(RabbitMQConfig.ORDER_CREATED_QUEUE);
        assertThat(dlqMessageCount()).as("recovered message is not dead-lettered").isZero();
    }

    @Test
    @DisplayName("A failure that never recovers stops after the attempt limit and is dead-lettered once")
    void retryExhaustionRoutesToDispatchDlq() {
        doThrow(new TransientDataAccessResourceException("database down"))
                .when(matchingEngine).processOrderCreated(any());
        UUID orderId = UUID.randomUUID();

        publishOrderCreated(orderCreated(orderId));

        await().atMost(Duration.ofSeconds(15)).until(() -> dlqMessageCount() == 1);
        verify(matchingEngine, after(1_500).times(CONFIGURED_MAX_ATTEMPTS)).processOrderCreated(any());
        assertQueuesDrained(RabbitMQConfig.ORDER_CREATED_QUEUE);

        Message deadLetter = rabbitTemplate.receive(DISPATCH_DLQ, 5_000);
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).contains(orderId.toString());
        assertThat(firstDeath(deadLetter))
                .containsEntry("queue", RabbitMQConfig.ORDER_CREATED_QUEUE)
                .containsEntry("reason", "rejected");
    }

    @Test
    @DisplayName("A permanent business failure is dead-lettered after one attempt, without retries")
    void permanentFailureIsDeadLetteredWithoutRetry() {
        doThrow(new InvalidAssignmentTransitionException("Cannot transition terminal assignment"))
                .when(dispatchAssignmentService).handleParcelStatusChanged(any());

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_PARCEL_STATUS,
                parcelStatusChanged(PublicOrderStatus.CANCELLED));

        await().atMost(Duration.ofSeconds(15)).until(() -> dlqMessageCount() == 1);
        verify(dispatchAssignmentService, after(1_500).times(1)).handleParcelStatusChanged(any());
        assertQueuesDrained(RabbitMQConfig.PARCEL_STATUS_QUEUE);
    }

    @Test
    @DisplayName("A malformed message is dead-lettered without reaching the business logic or being retried")
    void malformedMessageIsDeadLetteredWithoutProcessing() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED,
                new Message("{not-json".getBytes(StandardCharsets.UTF_8), properties));

        await().atMost(Duration.ofSeconds(15)).until(() -> dlqMessageCount() == 1);
        verify(matchingEngine, after(1_000).never()).processOrderCreated(any());
        assertQueuesDrained(RabbitMQConfig.ORDER_CREATED_QUEUE);
    }

    @Test
    @DisplayName("An order.created event without an orderId is dead-lettered without processing")
    void orderCreatedWithoutOrderIdIsDeadLettered() {
        publishOrderCreated(orderCreated(null));

        await().atMost(Duration.ofSeconds(15)).until(() -> dlqMessageCount() == 1);
        verify(matchingEngine, after(1_000).never()).processOrderCreated(any());
        assertQueuesDrained(RabbitMQConfig.ORDER_CREATED_QUEUE);
    }

    private void publishOrderCreated(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY_ORDER_CREATED, event);
    }

    private OrderCreatedEvent orderCreated(UUID orderId) {
        return new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), orderId,
                "SD-COL-" + UUID.randomUUID().toString().substring(0, 8), UUID.randomUUID(), UUID.randomUUID(),
                SriLankanDistrict.COLOMBO, SriLankanDistrict.GAMPAHA, OrderSpeed.STANDARD,
                null, null, "LKR", null, null, null, null, null);
    }

    private ParcelStatusChangedEvent parcelStatusChanged(PublicOrderStatus status) {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), UUID.randomUUID(), "SD-COL-00001", UUID.randomUUID(),
                null, PublicOrderStatus.OUT_FOR_DELIVERY, status, InternalQueueStatus.PICKUP_ASSIGNED,
                SriLankanDistrict.COLOMBO, SriLankanDistrict.GAMPAHA, null, Instant.now());
    }

    private void assertQueuesDrained(String queue) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            QueueInformation information = rabbitAdmin.getQueueInfo(queue);
            assertThat(information).isNotNull();
            assertThat(information.getMessageCount()).as("no message left to redeliver on " + queue).isZero();
        });
    }

    private int dlqMessageCount() {
        QueueInformation information = rabbitAdmin.getQueueInfo(DISPATCH_DLQ);
        return information == null ? 0 : information.getMessageCount();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstDeath(Message deadLetter) {
        List<Map<String, Object>> deaths =
                (List<Map<String, Object>>) deadLetter.getMessageProperties().getHeaders().get("x-death");
        assertThat(deaths).as("x-death header set by the broker").isNotEmpty();
        return deaths.getFirst();
    }

}
