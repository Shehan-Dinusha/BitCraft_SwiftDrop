package com.swiftdrop.webhook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.webhook.dto.CreateWebhookEndpointRequest;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.dto.WebhookEndpointResponse;
import com.swiftdrop.webhook.service.WebhookSubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookSubscriptionController.class)
class WebhookSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WebhookSubscriptionService subscriptionService;

    private final UUID merchantId = UUID.randomUUID();
    private final UUID endpointId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/webhooks/subscriptions/health should return 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/subscriptions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/subscriptions should return 201 Created")
    void testCreateEndpoint() throws Exception {
        CreateWebhookEndpointRequest request = new CreateWebhookEndpointRequest(
                merchantId,
                "https://merchant.example.com/api/v1/webhooks",
                "parcel.status.changed",
                "Live Webhook Listener"
        );

        WebhookEndpointResponse response = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                request.targetUrl(),
                "whsec_test_secret_123",
                request.subscribedEvents(),
                true,
                request.description(),
                Instant.now()
        );

        when(subscriptionService.createEndpoint(any(CreateWebhookEndpointRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/webhooks/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.endpointId").value(endpointId.toString()))
                .andExpect(jsonPath("$.data.secretKey").value("whsec_test_secret_123"));
    }

    @Test
    @DisplayName("GET /api/v1/webhooks/subscriptions/{endpointId} should return endpoint")
    void testGetEndpoint() throws Exception {
        WebhookEndpointResponse response = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                "https://merchant.example.com/api/v1/webhooks",
                "whsec_test_secret_123",
                "parcel.status.changed",
                true,
                "Listener",
                Instant.now()
        );

        when(subscriptionService.getEndpoint(endpointId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/webhooks/subscriptions/{endpointId}", endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.endpointId").value(endpointId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/webhooks/subscriptions should list endpoints for merchant")
    void testListEndpoints() throws Exception {
        WebhookEndpointResponse response = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                "https://merchant.example.com/api/v1/webhooks",
                "whsec_test_secret_123",
                "parcel.status.changed",
                true,
                "Listener",
                Instant.now()
        );

        when(subscriptionService.listEndpointsByMerchant(merchantId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/webhooks/subscriptions")
                        .param("merchantId", merchantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].endpointId").value(endpointId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/v1/webhooks/subscriptions/{endpointId}/status should toggle status")
    void testToggleStatus() throws Exception {
        WebhookEndpointResponse response = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                "https://merchant.example.com/api/v1/webhooks",
                "whsec_test_secret_123",
                "parcel.status.changed",
                false,
                "Listener",
                Instant.now()
        );

        when(subscriptionService.toggleEndpointActive(endpointId, false)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/webhooks/subscriptions/{endpointId}/status", endpointId)
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/webhooks/subscriptions/{endpointId} should return 200 OK")
    void testDeleteEndpoint() throws Exception {
        mockMvc.perform(delete("/api/v1/webhooks/subscriptions/{endpointId}", endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/subscriptions/{endpointId}/test should dispatch ping")
    void testTestPing() throws Exception {
        WebhookDeliveryAttemptResponse attemptResponse = new WebhookDeliveryAttemptResponse(
                UUID.randomUUID(), endpointId, merchantId, "webhook.test.ping", 200, 1, true, "OK", null, Instant.now()
        );

        when(subscriptionService.testPing(endpointId)).thenReturn(attemptResponse);

        mockMvc.perform(post("/api/v1/webhooks/subscriptions/{endpointId}/test", endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.httpStatusCode").value(200));
    }
}
