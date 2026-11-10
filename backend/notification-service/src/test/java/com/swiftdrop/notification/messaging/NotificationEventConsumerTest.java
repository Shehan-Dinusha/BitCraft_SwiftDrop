package com.swiftdrop.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.messaging.consumer.NotificationEventConsumer;
import com.swiftdrop.notification.messaging.trigger.EventEnvelope;
import com.swiftdrop.notification.messaging.trigger.NotificationTrigger;
import com.swiftdrop.notification.messaging.trigger.ParcelStatusTrigger;
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

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(objectMapper, dispatcher, List.of(new ParcelStatusTrigger()));
    }

    @Test
    @DisplayName("Should consume parcel status changed event and dispatch SMS notification via trigger")
    void testConsumePickedUpEvent() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-1111",
                    "newPublicStatus": "PICKED_UP"
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
    }

    @Test
    @DisplayName("Should dispatch EMAIL notification for DELIVERED status")
    void testConsumeDeliveredEvent() {
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-2222",
                    "newPublicStatus": "DELIVERED"
                }
                """.formatted(customerId);

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed.delivered");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        consumer.onNotificationEvent(message);

        ArgumentCaptor<DispatchNotificationRequest> captor = ArgumentCaptor.forClass(DispatchNotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());

        DispatchNotificationRequest request = captor.getValue();
        assertThat(request.templateCode()).isEqualTo("PARCEL_DELIVERED");
        assertThat(request.channel()).isEqualTo("EMAIL");
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

    @Test
    @DisplayName("Should invoke all matching triggers in observer pipeline")
    void testMultipleMatchingTriggers() {
        NotificationTrigger trigger1 = mock(NotificationTrigger.class);
        NotificationTrigger trigger2 = mock(NotificationTrigger.class);

        when(trigger1.matches(any(EventEnvelope.class))).thenReturn(true);
        when(trigger2.matches(any(EventEnvelope.class))).thenReturn(true);

        DispatchNotificationRequest req1 = new DispatchNotificationRequest(
                UUID.randomUUID(), "SMS", "TEMPLATE_1", "+94771111111", null, "REF-1");
        DispatchNotificationRequest req2 = new DispatchNotificationRequest(
                UUID.randomUUID(), "EMAIL", "TEMPLATE_2", "test@test.com", null, "REF-2");

        when(trigger1.build(any(EventEnvelope.class))).thenReturn(List.of(req1));
        when(trigger2.build(any(EventEnvelope.class))).thenReturn(List.of(req2));

        NotificationEventConsumer observerConsumer =
                new NotificationEventConsumer(objectMapper, dispatcher, List.of(trigger1, trigger2));

        String json = """
                {
                    "customerId": "%s",
                    "trackingNumber": "SD-9999",
                    "status": "CUSTOM"
                }
                """.formatted(UUID.randomUUID());

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("custom.event");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        observerConsumer.onNotificationEvent(message);

        verify(dispatcher).dispatch(req1);
        verify(dispatcher).dispatch(req2);
    }
}
