package com.swiftdrop.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.domain.entity.InAppNotification;
import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import com.swiftdrop.notification.domain.entity.RecipientPreference;
import com.swiftdrop.notification.domain.repository.InAppNotificationRepository;
import com.swiftdrop.notification.domain.repository.NotificationDispatchLogRepository;
import com.swiftdrop.notification.domain.repository.RecipientPreferenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

/**
 * End-to-end broker integration test using Testcontainers (PostgreSQL + RabbitMQ).
 * Verifies that publishing a DELIVERED domain event to swiftdrop.events.exchange
 * triggers consumer processing and persists a dispatch log in the database.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class BrokerNotificationIT {

    private static final String EXCHANGE = "swiftdrop.events.exchange";
    private static final String ROUTING_KEY = "parcel.status.changed.delivered";

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
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NotificationDispatchLogRepository logRepository;

    @Autowired
    private InAppNotificationRepository inAppRepository;

    @Autowired
    private RecipientPreferenceRepository preferenceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Publishing DELIVERED event to RabbitMQ broker should produce dispatch log in PostgreSQL")
    void testBrokerDeliveredEventProducesDispatchLog() {
        UUID customerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String trackingNumber = "SD-IT-DELIV-001";

        // Pre-configure recipient preference with email
        preferenceRepository.save(RecipientPreference.builder()
                .recipientId(customerId)
                .recipientType("CUSTOMER")
                .emailEnabled(true)
                .emailAddress("customer@example.com")
                .smsEnabled(true)
                .phoneNumber("+94771234567")
                .languagePreference("en")
                .build());

        String json = """
                {
                    "eventId": "%s",
                    "correlationId": "%s",
                    "customerId": "%s",
                    "trackingNumber": "%s",
                    "newPublicStatus": "DELIVERED",
                    "deliveredAt": "2026-11-10T09:00:00Z"
                }
                """.formatted(eventId, UUID.randomUUID(), customerId, trackingNumber);

        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        // Publish to the domain event exchange
        rabbitTemplate.send(EXCHANGE, ROUTING_KEY, message);

        // Await consumer execution and verify dispatch log is written to PostgreSQL
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<NotificationDispatchLog> logs = logRepository.findByRecipientIdOrderByCreatedAtDesc(customerId);
            assertThat(logs).isNotEmpty();

            NotificationDispatchLog log = logs.getFirst();
            assertThat(log.getTemplateCode()).isEqualTo("PARCEL_DELIVERED");
            assertThat(log.getChannel()).isEqualTo("EMAIL");
            assertThat(log.getReferenceId()).isEqualTo(trackingNumber);
            assertThat(log.getStatus()).isIn("SENT", "FAILED", "SUPPRESSED");

            List<InAppNotification> inAppRows = inAppRepository.findByUserIdOrderByCreatedAtDesc(customerId);
            assertThat(inAppRows).isNotEmpty();
            InAppNotification inApp = inAppRows.getFirst();
            assertThat(inApp.getTrackingNumber()).isEqualTo(trackingNumber);
            assertThat(inApp.isRead()).isFalse();
        });
    }
}
