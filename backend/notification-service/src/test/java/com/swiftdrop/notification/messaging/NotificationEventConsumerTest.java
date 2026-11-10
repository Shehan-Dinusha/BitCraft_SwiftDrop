package com.swiftdrop.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.domain.repository.ProcessedEventRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.messaging.consumer.NotificationEventConsumer;
import com.swiftdrop.notification.messaging.trigger.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MultiChannelNotificationDispatcher dispatcher;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(objectMapper, dispatcher, List.of(
                new PickedUpTrigger(),
                new OutForDeliveryTrigger(),
                new DeliveredTrigger(),
                new FailedTrigger()
        ), processedEventRepository);
    }

    @Test
    @DisplayName("Should consume parcel status changed event and dispatch SMS notification for PICKED_UP")
    void testConsumePickedUpEvent() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-1111",
                    "newPublicStatus": "PICKED_UP",
                    "courierName": "Kamal Perera"
                }
                """.formatted(customerId);

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed.picked_up");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        consumer.onNotificationEvent(message);

        ArgumentCaptor<DispatchNotificationRequest> captor = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());

        DispatchNotificationRequest request = captor.getValue();
        assertThat(request.recipientId()).isEqualTo(customerId);
        assertThat(request.templateCode()).isEqualTo("PARCEL_PICKED_UP");
        assertThat(request.channel()).isEqualTo("SMS");
        assertThat(request.referenceId()).isEqualTo("SD-1111");
        assertThat(request.templateParams()).containsEntry("courierName", "Kamal Perera");
    }

    @Test
    @DisplayName("Should dispatch SMS notification for OUT_FOR_DELIVERY status")
    void testConsumeOutForDeliveryEvent() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-3333",
                    "newPublicStatus": "OUT_FOR_DELIVERY",
                    "courierName": "Sunil Shantha"
                }
                """.formatted(customerId);

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed.out_for_delivery");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        consumer.onNotificationEvent(message);

        ArgumentCaptor<DispatchNotificationRequest> captor = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());

        DispatchNotificationRequest request = captor.getValue();
        assertThat(request.recipientId()).isEqualTo(customerId);
        assertThat(request.templateCode()).isEqualTo("PARCEL_OUT_FOR_DELIVERY");
        assertThat(request.channel()).isEqualTo("SMS");
        assertThat(request.templateParams()).containsEntry("courierName", "Sunil Shantha");
    }

    @Test
    @DisplayName("Should dispatch EMAIL notification for DELIVERED status")
    void testConsumeDeliveredEvent() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-2222",
                    "newPublicStatus": "DELIVERED",
                    "deliveredAt": "2026-11-10T08:30:00Z"
                }
                """.formatted(customerId);

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed.delivered");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        consumer.onNotificationEvent(message);

        ArgumentCaptor<DispatchNotificationRequest> captor = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());

        DispatchNotificationRequest request = captor.getValue();
        assertThat(request.recipientId()).isEqualTo(customerId);
        assertThat(request.templateCode()).isEqualTo("PARCEL_DELIVERED");
        assertThat(request.channel()).isEqualTo("EMAIL");
        assertThat(request.templateParams()).containsEntry("deliveredAt", "2026-11-10T08:30:00Z");
    }

    @Test
    @DisplayName("Should dispatch SMS notification for FAILED delivery status")
    void testConsumeFailedEvent() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-4444",
                    "newPublicStatus": "FAILED",
                    "failureReason": "Customer unavailable at delivery address"
                }
                """.formatted(customerId);

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed.failed");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        consumer.onNotificationEvent(message);

        ArgumentCaptor<DispatchNotificationRequest> captor = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());

        DispatchNotificationRequest request = captor.getValue();
        assertThat(request.recipientId()).isEqualTo(customerId);
        assertThat(request.templateCode()).isEqualTo("PARCEL_FAILED");
        assertThat(request.channel()).isEqualTo("SMS");
        assertThat(request.templateParams()).containsEntry("failureReason", "Customer unavailable at delivery address");
    }

    @Test
    @DisplayName("Should skip dispatch when recipientId or customerId is missing")
    void testConsumeEventWithoutRecipient() {
        String json = """
                {
                    "trackingNumber": "SD-3333",
                    "newPublicStatus": "PICKED_UP"
                }
                """;

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed.picked_up");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        consumer.onNotificationEvent(message);

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Should gracefully handle corrupted JSON without throwing exception")
    void testConsumeMalformedJson() {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed.corrupted");
        Message message = new Message("not-a-valid-json".getBytes(StandardCharsets.UTF_8), props);

        consumer.onNotificationEvent(message);

        verify(dispatcher, never()).dispatch(any());
    }
}
