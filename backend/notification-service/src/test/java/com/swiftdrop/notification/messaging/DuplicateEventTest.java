package com.swiftdrop.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.domain.entity.ProcessedEvent;
import com.swiftdrop.notification.domain.repository.ProcessedEventRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.messaging.consumer.NotificationEventConsumer;
import com.swiftdrop.notification.messaging.trigger.PickedUpTrigger;
import com.swiftdrop.notification.service.MultiChannelNotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuplicateEventTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MultiChannelNotificationDispatcher dispatcher;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(
                objectMapper,
                dispatcher,
                List.of(new PickedUpTrigger()),
                processedEventRepository
        );
    }

    @Test
    @DisplayName("Duplicate event with identical eventId should be ignored on second arrival")
    void testDuplicateEventIsIgnored() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String json = """
                {
                    "eventId": "%s",
                    "customerId": "%s",
                    "trackingNumber": "SD-DUP-001",
                    "newPublicStatus": "PICKED_UP",
                    "courierName": "Kasun"
                }
                """.formatted(eventId, customerId);

        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed.picked_up");
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

        // First message arrival: eventId is not yet processed
        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onNotificationEvent(message);

        // Verification for 1st message: notification dispatched and event saved
        verify(dispatcher, times(1)).dispatch(any(DispatchNotificationRequest.class));
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));

        // Second message arrival: eventId is now recorded as processed
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.onNotificationEvent(message);

        // Verification for 2nd message: dispatcher not called again (still total = 1)
        verify(dispatcher, times(1)).dispatch(any(DispatchNotificationRequest.class));
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }
}
