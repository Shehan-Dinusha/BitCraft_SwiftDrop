package com.swiftdrop.webhook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.enums.UserRole;
import com.swiftdrop.contracts.security.CallerContext;
import com.swiftdrop.contracts.security.ForbiddenException;
import com.swiftdrop.webhook.dto.CreateWebhookEndpointRequest;
import com.swiftdrop.webhook.dto.WebhookDeliveryAttemptResponse;
import com.swiftdrop.webhook.dto.WebhookEndpointResponse;
import com.swiftdrop.webhook.exception.InvalidEventSubscriptionException;
import com.swiftdrop.webhook.exception.WebhookEndpointNotFoundException;
import com.swiftdrop.webhook.service.WebhookSubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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

    private final UUID userId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID endpointId = UUID.randomUUID();

    /** Headers exactly as the API gateway injects them after verifying a merchant's JWT. */
    private MockHttpServletRequestBuilder asMerchant(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", userId.toString())
                .header("X-User-Role", "MERCHANT")
                .header("X-Merchant-Id", merchantId.toString());
    }

    private WebhookEndpointResponse endpoint(boolean active) {
        return new WebhookEndpointResponse(endpointId, merchantId, "https://merchant.example.com/api/v1/webhooks",
                "whsec_test_secret_123", "parcel.status.changed", active, "Listener", Instant.now());
    }

    private CallerContext capturedCaller(ArgumentCaptor<CallerContext> captor) {
        CallerContext caller = captor.getValue();
        assertThat(caller.userId()).isEqualTo(userId);
        assertThat(caller.role()).isEqualTo(UserRole.MERCHANT);
        assertThat(caller.merchantId()).isEqualTo(merchantId);
        return caller;
    }

    @Test
    @DisplayName("GET /health needs no identity")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/subscriptions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST without merchantId registers with the gateway-supplied caller and returns 201")
    void testCreateEndpoint() throws Exception {
        CreateWebhookEndpointRequest request = new CreateWebhookEndpointRequest(
                null, "https://merchant.example.com/api/v1/webhooks", "parcel.status.changed", "Live Webhook Listener");
        when(subscriptionService.createEndpoint(any(), any())).thenReturn(endpoint(true));

        mockMvc.perform(asMerchant(post("/api/v1/webhooks/subscriptions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endpointId").value(endpointId.toString()))
                .andExpect(jsonPath("$.data.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.data.secretKey").value("whsec_test_secret_123"));

        ArgumentCaptor<CallerContext> caller = ArgumentCaptor.forClass(CallerContext.class);
        verify(subscriptionService).createEndpoint(eq(request), caller.capture());
        capturedCaller(caller);
    }

    @Test
    @DisplayName("requests without gateway identity headers are rejected with 401 before reaching the service")
    void missingIdentityIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/subscriptions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/webhooks/subscriptions/{endpointId}", endpointId)
                        .header("X-User-Role", "MERCHANT").header("X-Merchant-Id", merchantId.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/webhooks/subscriptions")
                        .header("X-User-Id", userId.toString()).header("X-User-Role", "ADMIN_OPS"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("a role or ownership refusal from the service is a generic 403")
    void forbiddenIsMapped() throws Exception {
        when(subscriptionService.listEndpoints(any(), any()))
                .thenThrow(new ForbiddenException("Caller cannot access this merchant's resources"));

        mockMvc.perform(asMerchant(get("/api/v1/webhooks/subscriptions")).param("merchantId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not permitted to perform this operation"));
    }

    @Test
    @DisplayName("another merchant's endpoint answers 404")
    void otherMerchantEndpointIsNotFound() throws Exception {
        when(subscriptionService.getEndpoint(eq(endpointId), any()))
                .thenThrow(new WebhookEndpointNotFoundException("Webhook endpoint not found with ID: " + endpointId));

        mockMvc.perform(asMerchant(get("/api/v1/webhooks/subscriptions/{endpointId}", endpointId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET list passes the optional filter and caller to the service")
    void testListEndpoints() throws Exception {
        when(subscriptionService.listEndpoints(any(), any())).thenReturn(List.of(endpoint(true)));

        mockMvc.perform(asMerchant(get("/api/v1/webhooks/subscriptions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].endpointId").value(endpointId.toString()));

        ArgumentCaptor<CallerContext> caller = ArgumentCaptor.forClass(CallerContext.class);
        verify(subscriptionService).listEndpoints(eq(null), caller.capture());
        capturedCaller(caller);
    }

    @Test
    @DisplayName("a client-sent X-Merchant-Id is not read as a list filter")
    void merchantHeaderIsNotAFilter() throws Exception {
        when(subscriptionService.listEndpoints(any(), any())).thenReturn(List.of());

        mockMvc.perform(asMerchant(get("/api/v1/webhooks/subscriptions")))
                .andExpect(status().isOk());

        // The header only feeds the caller identity; the filter argument stays null.
        verify(subscriptionService).listEndpoints(eq(null), any(CallerContext.class));
    }

    @Test
    @DisplayName("GET, PATCH, DELETE, test and attempts all pass the caller to the service")
    void singleEndpointOperationsPassCaller() throws Exception {
        when(subscriptionService.getEndpoint(eq(endpointId), any())).thenReturn(endpoint(true));
        when(subscriptionService.toggleEndpointActive(eq(endpointId), eq(false), any())).thenReturn(endpoint(false));
        when(subscriptionService.testPing(eq(endpointId), any())).thenReturn(new WebhookDeliveryAttemptResponse(
                UUID.randomUUID(), endpointId, merchantId, "webhook.test.ping", 200, 1, true, "OK", null, Instant.now()));
        when(subscriptionService.getDeliveryAttempts(eq(endpointId), any())).thenReturn(List.of());

        mockMvc.perform(asMerchant(get("/api/v1/webhooks/subscriptions/{endpointId}", endpointId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.endpointId").value(endpointId.toString()));
        mockMvc.perform(asMerchant(patch("/api/v1/webhooks/subscriptions/{endpointId}/status", endpointId))
                        .param("active", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(asMerchant(delete("/api/v1/webhooks/subscriptions/{endpointId}", endpointId)))
                .andExpect(status().isOk());
        mockMvc.perform(asMerchant(post("/api/v1/webhooks/subscriptions/{endpointId}/test", endpointId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.httpStatusCode").value(200));
        mockMvc.perform(asMerchant(get("/api/v1/webhooks/subscriptions/{endpointId}/attempts", endpointId)))
                .andExpect(status().isOk());

        ArgumentCaptor<CallerContext> caller = ArgumentCaptor.forClass(CallerContext.class);
        verify(subscriptionService).deleteEndpoint(eq(endpointId), caller.capture());
        capturedCaller(caller);
        verify(subscriptionService).testPing(eq(endpointId), caller.capture());
        capturedCaller(caller);
        verify(subscriptionService).getDeliveryAttempts(eq(endpointId), caller.capture());
        capturedCaller(caller);
    }

    @Test
    @DisplayName("DELETE of an endpoint with delivery history returns 409 without leaking database details")
    void testDeleteEndpointWithDeliveryHistoryConflicts() throws Exception {
        doThrow(new DataIntegrityViolationException("violates foreign key constraint fk_webhook_deliveries_endpoint_merchant"))
                .when(subscriptionService).deleteEndpoint(eq(endpointId), any());

        mockMvc.perform(asMerchant(delete("/api/v1/webhooks/subscriptions/{endpointId}", endpointId)))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("fk_webhook_deliveries"))));
    }

    @Test
    @DisplayName("POST with invalid subscription patterns returns 400")
    void testCreateEndpointWithInvalidSubscriptions() throws Exception {
        CreateWebhookEndpointRequest request = new CreateWebhookEndpointRequest(
                null, "https://merchant.example.com/api/v1/webhooks", "parcel.#", null);
        when(subscriptionService.createEndpoint(any(), any()))
                .thenThrow(new InvalidEventSubscriptionException("Unsupported event subscription(s): parcel.#"));

        mockMvc.perform(asMerchant(post("/api/v1/webhooks/subscriptions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported event subscription(s): parcel.#"));
    }

    @Test
    @DisplayName("Unexpected errors return a generic message, not the exception text")
    void testUnexpectedErrorDoesNotLeakMessage() throws Exception {
        when(subscriptionService.getEndpoint(eq(endpointId), any()))
                .thenThrow(new IllegalStateException("connection to db-host-internal:5432 refused"));

        mockMvc.perform(asMerchant(get("/api/v1/webhooks/subscriptions/{endpointId}", endpointId)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("db-host-internal"))));
    }
}
