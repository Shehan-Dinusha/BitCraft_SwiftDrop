package com.swiftdrop.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.domain.repository.ProcessedEventRepository;
import com.swiftdrop.notification.dto.DispatchNotificationRequest;
import com.swiftdrop.notification.messaging.consumer.NotificationEventConsumer;
import com.swiftdrop.notification.messaging.trigger.*;
import com.swiftdrop.notification.service.InAppNotificationService;
import com.swiftdrop.notification.service.MerchantDirectory;
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

    @Mock
    private InAppNotificationService inAppNotificationService;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(objectMapper, dispatcher, List.of(
                new PickedUpTrigger(),
                new OutForDeliveryTrigger(),
                new DeliveredTrigger(),
                new FailedTrigger()
        ), processedEventRepository, inAppNotificationService);
    }

    @Test
    @DisplayName("Should consume parcel status changed event and dispatch EMAIL notification for PICKED_UP")
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
        assertThat(request.channel()).isEqualTo("EMAIL");
        assertThat(request.referenceId()).isEqualTo("SD-1111");
        assertThat(request.templateParams()).containsEntry("courierName", "Kamal Perera");
        verify(inAppNotificationService).saveNotification(eq(customerId), eq("PARCEL_PICKED_UP"), any(), any(), eq("SD-1111"));
    }

    @Test
    @DisplayName("Should dispatch EMAIL notification for OUT_FOR_DELIVERY status")
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

        assertThat(captor.getAllValues()).extracting(DispatchNotificationRequest::channel).containsExactly("EMAIL");
        assertThat(captor.getAllValues()).allSatisfy(request -> {
            assertThat(request.recipientId()).isEqualTo(customerId);
            assertThat(request.templateCode()).isEqualTo("PARCEL_OUT_FOR_DELIVERY");
            assertThat(request.templateParams()).containsEntry("courierName", "Sunil Shantha");
        });
        verify(inAppNotificationService, times(1)).saveNotification(eq(customerId), eq("PARCEL_OUT_FOR_DELIVERY"), any(), any(), eq("SD-3333"));
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
        assertThat(request.templateParams()).containsEntry("deliveredAt", "10 Nov 2026, 2:00 p.m.");
    }

    @Test
    @DisplayName("Should dispatch EMAIL notification for FAILED delivery status")
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

        assertThat(captor.getAllValues()).extracting(DispatchNotificationRequest::channel).containsExactly("EMAIL");
        assertThat(captor.getAllValues()).allSatisfy(request -> {
            assertThat(request.recipientId()).isEqualTo(customerId);
            assertThat(request.templateCode()).isEqualTo("PARCEL_FAILED");
            assertThat(request.templateParams()).containsEntry("failureReason", "Customer unavailable at delivery address");
        });
        verify(inAppNotificationService, times(1)).saveNotification(eq(customerId), eq("PARCEL_FAILED"), any(), any(), eq("SD-4444"));
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
    @DisplayName("Team notifications are written once per audience; a failed dispatch reaches the operations team")
    void writesTeamNotifications() {
        NotificationEventConsumer teamConsumer = new NotificationEventConsumer(objectMapper, dispatcher,
                List.of(new com.swiftdrop.notification.messaging.trigger.DispatchFailedTrigger()),
                processedEventRepository, inAppNotificationService);
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("dispatch.failed");
        String json = "{\"eventId\":\"%s\",\"orderId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"failureReason\":\"No courier\"}"
                .formatted(UUID.randomUUID());

        teamConsumer.onNotificationEvent(new Message(json.getBytes(StandardCharsets.UTF_8), props));

        verify(inAppNotificationService).saveTeamNotification(eq("OPERATIONS"), eq("DISPATCH_FAILED"),
                eq("Dispatch needs attention"), any(), any());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Sign-in alerts are emailed but never copied to the in-app inbox")
    void loginAlertsSkipTheInbox() {
        UUID userId = UUID.randomUUID();
        NotificationEventConsumer loginConsumer = new NotificationEventConsumer(objectMapper, dispatcher,
                List.of(new com.swiftdrop.notification.messaging.trigger.AuthLoginTrigger()),
                processedEventRepository, inAppNotificationService);
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("notification.auth.login");
        String json = "{\"eventId\":\"%s\",\"recipientId\":\"%s\",\"outcome\":\"SUCCESS\"}"
                .formatted(UUID.randomUUID(), userId);

        loginConsumer.onNotificationEvent(new Message(json.getBytes(StandardCharsets.UTF_8), props));

        verify(dispatcher).dispatch(any());
        verify(inAppNotificationService, never()).saveNotification(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A merchant parcel is never emailed to the company id; the merchant and operations teams are told")
    void merchantParcelGoesToTeamInboxes() {
        UUID merchantId = UUID.randomUUID();
        MerchantDirectory merchants = mock(MerchantDirectory.class);
        when(merchants.isMerchant(merchantId)).thenReturn(true);
        NotificationEventConsumer merchantConsumer = new NotificationEventConsumer(objectMapper, dispatcher,
                List.of(new FailedTrigger()), processedEventRepository, inAppNotificationService, merchants);
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed");
        String json = """
                {"customerId":"%s","trackingNumber":"SD-4444-5555","newPublicStatus":"FAILED","failureReason":"WRONG_ADDRESS"}
                """.formatted(merchantId);

        merchantConsumer.onNotificationEvent(new Message(json.getBytes(StandardCharsets.UTF_8), props));

        verify(dispatcher, never()).dispatch(any());
        verify(inAppNotificationService, never()).saveNotification(any(), any(), any(), any(), any());
        verify(inAppNotificationService).saveTeamNotification(eq("OPERATIONS"), eq("OPS_DELIVERY_FAILED"),
                eq("Delivery failed"), contains("the delivery address could not be found"), eq("SD-4444-5555"));
        verify(inAppNotificationService).saveTeamNotification(eq("MERCHANT:" + merchantId), eq("MERCHANT_DELIVERY_FAILED"),
                eq("Delivery failed"), any(), eq("SD-4444-5555"));
    }

    @Test
    @DisplayName("A customer's failed delivery never creates a merchant row for the customer's own id")
    void customerParcelHasNoMerchantRow() {
        UUID customerId = UUID.randomUUID();
        MerchantDirectory merchants = mock(MerchantDirectory.class);
        NotificationEventConsumer c = new NotificationEventConsumer(objectMapper, dispatcher,
                List.of(new FailedTrigger()), processedEventRepository, inAppNotificationService, merchants);
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("parcel.status.changed");
        String json = "{\"customerId\":\"%s\",\"trackingNumber\":\"SD-1\",\"newPublicStatus\":\"FAILED\"}".formatted(customerId);

        c.onNotificationEvent(new Message(json.getBytes(StandardCharsets.UTF_8), props));

        verify(dispatcher).dispatch(any());
        verify(inAppNotificationService).saveTeamNotification(eq("OPERATIONS"), any(), any(), any(), any());
        verify(inAppNotificationService, never()).saveTeamNotification(startsWith("MERCHANT:"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("A merchant order is remembered and its order confirmation is not emailed to the company id")
    void merchantOrderIsRememberedAndNotEmailed() {
        UUID merchantId = UUID.randomUUID();
        MerchantDirectory merchants = mock(MerchantDirectory.class);
        when(merchants.isMerchant(merchantId)).thenReturn(true);
        NotificationEventConsumer c = new NotificationEventConsumer(objectMapper, dispatcher,
                List.of(new OrderCreatedTrigger()), processedEventRepository, inAppNotificationService, merchants);
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey("order.created");
        String json = "{\"customerId\":\"%s\",\"merchantId\":\"%s\",\"trackingNumber\":\"SD-2\"}".formatted(merchantId, merchantId);

        c.onNotificationEvent(new Message(json.getBytes(StandardCharsets.UTF_8), props));

        verify(merchants).remember(merchantId);
        verify(dispatcher, never()).dispatch(any());
        verify(inAppNotificationService, never()).saveTeamNotification(any(), any(), any(), any(), any());
    }
}
