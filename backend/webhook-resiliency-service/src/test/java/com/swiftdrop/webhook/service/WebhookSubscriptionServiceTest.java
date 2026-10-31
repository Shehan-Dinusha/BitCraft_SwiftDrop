package com.swiftdrop.webhook.service;

import com.swiftdrop.webhook.domain.entity.WebhookDeliveryAttempt;
import com.swiftdrop.webhook.domain.entity.WebhookEndpoint;
import com.swiftdrop.webhook.domain.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.webhook.domain.repository.WebhookEndpointRepository;
import com.swiftdrop.webhook.dto.CreateWebhookEndpointRequest;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.dto.WebhookEndpointResponse;
import com.swiftdrop.webhook.exception.WebhookEndpointNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookSubscriptionServiceTest {

    @Mock
    private WebhookEndpointRepository endpointRepository;

    @Mock
    private WebhookDeliveryAttemptRepository attemptRepository;

    @Mock
    private OutboundWebhookDispatcher dispatcher;

    @InjectMocks
    private WebhookSubscriptionService subscriptionService;

    private UUID merchantId;
    private UUID endpointId;
    private WebhookEndpoint sampleEndpoint;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
        sampleEndpoint = WebhookEndpoint.builder()
                .endpointId(endpointId)
                .merchantId(merchantId)
                .targetUrl("https://merchant.example.com/webhooks")
                .secretKey("whsec_abc123")
                .subscribedEvents("parcel.status.changed")
                .active(true)
                .description("Sample Endpoint")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("createEndpoint should generate whsec_ secret and persist endpoint")
    void testCreateEndpoint() {
        CreateWebhookEndpointRequest request = new CreateWebhookEndpointRequest(
                merchantId,
                "https://merchant.example.com/webhooks",
                "parcel.status.changed,order.created",
                "ERP Webhook"
        );

        when(endpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> {
            WebhookEndpoint ep = invocation.getArgument(0);
            return ep;
        });

        WebhookEndpointResponse response = subscriptionService.createEndpoint(request);

        assertThat(response).isNotNull();
        assertThat(response.merchantId()).isEqualTo(merchantId);
        assertThat(response.targetUrl()).isEqualTo("https://merchant.example.com/webhooks");
        assertThat(response.secretKey()).startsWith("whsec_");
        assertThat(response.subscribedEvents()).isEqualTo("parcel.status.changed,order.created");
        assertThat(response.active()).isTrue();
        verify(endpointRepository).save(any(WebhookEndpoint.class));
    }

    @Test
    @DisplayName("getEndpoint should return DTO when found")
    void testGetEndpointFound() {
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.of(sampleEndpoint));

        WebhookEndpointResponse response = subscriptionService.getEndpoint(endpointId);

        assertThat(response).isNotNull();
        assertThat(response.endpointId()).isEqualTo(endpointId);
        assertThat(response.merchantId()).isEqualTo(merchantId);
    }

    @Test
    @DisplayName("getEndpoint should throw WebhookEndpointNotFoundException when missing")
    void testGetEndpointNotFound() {
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getEndpoint(endpointId))
                .isInstanceOf(WebhookEndpointNotFoundException.class)
                .hasMessageContaining("Webhook endpoint not found with ID");
    }

    @Test
    @DisplayName("listEndpointsByMerchant should return endpoints for merchant")
    void testListEndpointsByMerchant() {
        when(endpointRepository.findByMerchantId(merchantId)).thenReturn(List.of(sampleEndpoint));

        List<WebhookEndpointResponse> list = subscriptionService.listEndpointsByMerchant(merchantId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).endpointId()).isEqualTo(endpointId);
    }

    @Test
    @DisplayName("toggleEndpointActive should update active status")
    void testToggleEndpointActive() {
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.of(sampleEndpoint));
        when(endpointRepository.save(any(WebhookEndpoint.class))).thenReturn(sampleEndpoint);

        assertThat(sampleEndpoint.isActive()).isFalse();
        verify(endpointRepository).save(sampleEndpoint);
    }

    @Test
    @DisplayName("deleteEndpoint should delete endpoint if found")
    void testDeleteEndpoint() {
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.of(sampleEndpoint));

        subscriptionService.deleteEndpoint(endpointId);

        verify(endpointRepository).delete(sampleEndpoint);
    }

    @Test
    @DisplayName("testPing should dispatch ping payload and return attempt response")
    void testTestPing() {
        when(endpointRepository.findByEndpointId(endpointId)).thenReturn(Optional.of(sampleEndpoint));
        WebhookDeliveryAttemptResponse attemptResponse = new WebhookDeliveryAttemptResponse(
                UUID.randomUUID(), endpointId, merchantId, "webhook.test.ping", 200, 1, true, "OK", null, Instant.now()
        );
        when(dispatcher.dispatch(eq(sampleEndpoint), eq("webhook.test.ping"), any())).thenReturn(attemptResponse);

        WebhookDeliveryAttemptResponse result = subscriptionService.testPing(endpointId);

        assertThat(result).isNotNull();
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.success()).isTrue();
        verify(dispatcher).dispatch(eq(sampleEndpoint), eq("webhook.test.ping"), any());
    }

    @Test
    @DisplayName("getDeliveryAttempts should return attempts history")
    void testGetDeliveryAttempts() {
        WebhookDeliveryAttempt attempt = WebhookDeliveryAttempt.builder()
                .attemptId(UUID.randomUUID())
                .endpointId(endpointId)
                .merchantId(merchantId)
                .eventType("parcel.status.changed")
                .httpStatusCode(200)
                .attemptNumber(1)
                .success(true)
                .attemptedAt(Instant.now())
                .build();

        when(attemptRepository.findByEndpointIdOrderByAttemptedAtDesc(endpointId)).thenReturn(List.of(attempt));

        List<WebhookDeliveryAttemptResponse> attempts = subscriptionService.getDeliveryAttempts(endpointId);

        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).endpointId()).isEqualTo(endpointId);
        assertThat(attempts.get(0).success()).isTrue();
    }
}
