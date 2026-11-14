package com.swiftdrop.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.domain.repository.ProcessedEventRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.messaging.consumer.NotificationEventConsumer;
import com.swiftdrop.notification.messaging.trigger.OrderCreatedTrigger;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCreatedNotificationIT {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MultiChannelNotificationDispatcher dispatcher;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        OrderCreatedTrigger orderCreatedTrigger = new OrderCreatedTrigger();
        consumer = new NotificationEventConsumer(objectMapper, dispatcher, List.of(orderCreatedTrigger), processedEventRepository);
    }

    @Test
    @DisplayName("order.created event should trigger customer SMS and EMAIL order confirmation")
    void testOrderCreatedDispatchesSmsNotification() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String trackingNumber = "SD-2026-COL-999";

        String json = """
                {
                    "eventId": "%s",
                    "correlationId": "%s",
                    "orderId": "%s",
                    "trackingNumber": "%s",
                    "customerId": "%s",
                    "pickupDistrict": "COLOMBO",
                    "deliveryDistrict": "GAMPAHA",
                    "speed": "EXPRESS"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), orderId, trackingNumber, customerId);

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("order.created");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        consumer.onNotificationEvent(message);

        ArgumentCaptor<DispatchNotificationRequest> captor = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        verify(dispatcher, times(2)).dispatch(captor.capture());

        // Order confirmation goes out by SMS (simulated) and EMAIL (mock or Azure provider).
        assertThat(captor.getAllValues()).extracting(DispatchNotificationRequest::channel).containsExactly("SMS", "EMAIL");
        assertThat(captor.getAllValues()).allSatisfy(request -> {
            assertThat(request.recipientId()).isEqualTo(customerId);
            assertThat(request.templateCode()).isEqualTo("PARCEL_CREATED");
            assertThat(request.referenceId()).isEqualTo(trackingNumber);
            assertThat(request.templateParams()).containsEntry("trackingNumber", trackingNumber);
            assertThat(request.templateParams()).containsEntry("pickupDistrict", "COLOMBO");
            assertThat(request.templateParams()).containsEntry("deliveryDistrict", "GAMPAHA");
            assertThat(request.templateParams()).containsEntry("speed", "EXPRESS");
            assertThat(request.templateParams()).containsEntry("orderId", orderId.toString());
        });
    }
}
