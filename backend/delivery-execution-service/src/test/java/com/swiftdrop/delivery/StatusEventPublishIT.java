package com.swiftdrop.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.DeliveryState;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.DeliverySession;
import com.swiftdrop.delivery.domain.repository.DeliverySessionRepository;
import com.swiftdrop.delivery.dto.TransitionDeliveryRequest;
import com.swiftdrop.delivery.service.DeliveryExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Event integration test: a real PostgreSQL and RabbitMQ managed via Testcontainers.
 * Verifies that a valid delivery transition publishes a message on the topic exchange
 * matching "parcel.status.changed.#" with the correct tracking number.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class StatusEventPublishIT {

    private static final String EXCHANGE = "swiftdrop.events.exchange";
    private static final String ROUTING_KEY_PATTERN = "parcel.status.changed.#";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired
    private DeliveryExecutionService deliveryService;

    @Autowired
    private DeliverySessionRepository deliverySessionRepository;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String queueName;

    @BeforeEach
    void setUpTestQueue() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        rabbitAdmin.declareExchange(exchange);

        queueName = "it.parcel-status." + UUID.randomUUID();
        Queue queue = new Queue(queueName, false, true, true);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_PATTERN));
    }

    @Test
    @DisplayName("Valid transition publishes parcel.status.changed event with tracking number")
    void testTransitionPublishesStatusChangeEvent() {
        UUID deliveryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        String trackingNumber = "SD-COL-" + System.currentTimeMillis();

        DeliverySession session = new DeliverySession();
        session.setDeliveryId(deliveryId);
        session.setOrderId(orderId);
        session.setTrackingNumber(trackingNumber);
        session.setCourierId(courierId);
        session.setCourierName("Kamal Silva");
        session.setCurrentState(DeliveryState.ASSIGNED);
        session.setStatus("ASSIGNED");
        session.setPickupDistrict(SriLankanDistrict.COLOMBO);
        session.setDeliveryDistrict(SriLankanDistrict.KANDY);
        deliverySessionRepository.save(session);

        // Perform valid transition: ASSIGNED -> PICKED_UP
        TransitionDeliveryRequest request = new TransitionDeliveryRequest(
                DeliveryState.PICKED_UP,
                "Package collected from merchant",
                BigDecimal.valueOf(6.9271),
                BigDecimal.valueOf(79.8612),
                null,
                "COURIER"
        );

        deliveryService.transition(deliveryId, request);

        // Await published event on test queue
        List<JsonNode> receivedEvents = new ArrayList<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            Message message = rabbitTemplate.receive(queueName);
            if (message != null) {
                receivedEvents.add(objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8)));
            }
            return !receivedEvents.isEmpty();
        });

        assertThat(receivedEvents).hasSize(1);
        JsonNode eventJson = receivedEvents.get(0);
        assertThat(eventJson.get("trackingNumber").asText()).isEqualTo(trackingNumber);
        assertThat(eventJson.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(eventJson.get("newPublicStatus").asText()).isIn("PICKED_UP", "IN_TRANSIT");
    }
}

