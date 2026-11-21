package com.swiftdrop.notification.messaging;

import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import com.swiftdrop.notification.domain.entity.PushSubscription;
import com.swiftdrop.notification.domain.repository.InAppNotificationRepository;
import com.swiftdrop.notification.domain.repository.NotificationDispatchLogRepository;
import com.swiftdrop.notification.domain.repository.ProcessedEventRepository;
import com.swiftdrop.notification.domain.repository.PushSubscriptionRepository;
import com.swiftdrop.notification.service.push.WebPushClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dispatch.courier.assigned through a real RabbitMQ and PostgreSQL (V11 included): the courier gets one
 * inbox row and one device push, also when the broker delivers the same event twice. Only the outbound
 * HTTP call to the push service is replaced.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class CourierAssignedPushIT {

    private static final String EXCHANGE = "swiftdrop.events.exchange";

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

    @MockitoBean
    private WebPushClient webPushClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private PushSubscriptionRepository subscriptionRepository;
    @Autowired
    private InAppNotificationRepository inAppRepository;
    @Autowired
    private NotificationDispatchLogRepository logRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    @DisplayName("A redelivered courier assignment pushes and notifies the courier exactly once")
    void courierAssignedPushedOnceEvenOnRedelivery() throws Exception {
        UUID courierId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String endpoint = "https://fcm.googleapis.com/fcm/send/courier-phone";
        when(webPushClient.enabled()).thenReturn(true);
        when(webPushClient.send(any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(201);
        subscriptionRepository.save(PushSubscription.builder()
                .recipientId(courierId)
                .endpoint(endpoint)
                .p256dh("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4")
                .auth("BTBZMqHH6r4Tts7J_aSIgg")
                .build());

        String json = """
                {"eventId":"%s","correlationId":"%s","orderId":"%s","courierId":"%s","courierName":"Kamal",
                 "trackingNumber":"SD-IT-CRR-0001","estimatedPickupMinutes":20,
                 "pickupDistrict":"COLOMBO","deliveryDistrict":"KANDY"}
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID(), courierId);
        MessageProperties props = new MessageProperties();
        props.setContentType("application/json");

        rabbitTemplate.send(EXCHANGE, "dispatch.courier.assigned", new Message(json.getBytes(StandardCharsets.UTF_8), props));
        await().atMost(Duration.ofSeconds(15)).until(() -> processedEventRepository.existsById(eventId));

        // The broker redelivers the same event.
        rabbitTemplate.send(EXCHANGE, "dispatch.courier.assigned", new Message(json.getBytes(StandardCharsets.UTF_8), props));

        // Stays at exactly one of each while the duplicate is consumed.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            assertThat(inAppRepository.findByUserIdOrderByCreatedAtDesc(courierId)).hasSize(1);
            List<NotificationDispatchLog> logs = logRepository.findByRecipientIdOrderByCreatedAtDesc(courierId);
            assertThat(logs).extracting(NotificationDispatchLog::getChannel).containsExactlyInAnyOrder("EMAIL", "PUSH");
            assertThat(logs).filteredOn(l -> "PUSH".equals(l.getChannel()))
                    .singleElement().satisfies(l -> {
                        assertThat(l.getStatus()).isEqualTo("SENT");
                        assertThat(l.getTemplateCode()).isEqualTo("COURIER_ASSIGNED");
                        assertThat(l.getDestination()).isEqualTo(courierId.toString()); // never the endpoint
                    });
        });
        verify(webPushClient, times(1)).send(eq(endpoint), any(), any(), any(), eq(15 * 60), eq("high"), eq("SD-IT-CRR-0001"));
        assertThat(subscriptionRepository.findByEndpoint(endpoint)).hasValueSatisfying(s ->
                assertThat(s.getLastSuccessAt()).isNotNull());
    }
}
