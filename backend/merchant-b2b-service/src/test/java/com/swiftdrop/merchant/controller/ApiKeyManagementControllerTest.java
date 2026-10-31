package com.swiftdrop.merchant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.contracts.dto.MerchantApiKeyValidationRequest;
import com.swiftdrop.contracts.dto.MerchantApiKeyValidationResult;
import com.swiftdrop.merchant.dto.ApiKeyResponse;
import com.swiftdrop.merchant.dto.CreateApiKeyRequest;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
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

@WebMvcTest(ApiKeyManagementController.class)
class ApiKeyManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MerchantApiKeyService apiKeyService;

    private final UUID merchantId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/api-key-management/health should return 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/api-key-management/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/merchants/{merchantId}/api-keys should return 201 Created with raw key")
    void testGenerateApiKey() throws Exception {
        CreateApiKeyRequest request = new CreateApiKeyRequest("Test Key", 60);

        ApiKeyResponse response = new ApiKeyResponse(
                UUID.randomUUID(),
                merchantId,
                "Test Key",
                "sk_live_123...",
                true,
                Instant.now(),
                Instant.now().plusSeconds(86400 * 60),
                null,
                null,
                "sk_live_1234567890abcdef"
        );

        when(apiKeyService.generateApiKey(eq(merchantId), any(CreateApiKeyRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/merchants/" + merchantId + "/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keyName").value("Test Key"))
                .andExpect(jsonPath("$.data.rawApiKey").value("sk_live_1234567890abcdef"));
    }

    @Test
    @DisplayName("GET /api/v1/merchants/{merchantId}/api-keys should return 200 OK with key list")
    void testListApiKeys() throws Exception {
        ApiKeyResponse key = new ApiKeyResponse(
                UUID.randomUUID(),
                merchantId,
                "Test Key",
                "sk_live_abc...",
                true,
                Instant.now(),
                null,
                null,
                null,
                null
        );

        when(apiKeyService.listApiKeys(merchantId)).thenReturn(List.of(key));

        mockMvc.perform(get("/api/v1/merchants/" + merchantId + "/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].keyName").value("Test Key"));
    }

    @Test
    @DisplayName("POST /internal/v1/api-keys/validate should return validation result")
    void testValidateInternalApiKey() throws Exception {
        MerchantApiKeyValidationRequest req = new MerchantApiKeyValidationRequest("sk_live_valid_token");
        MerchantApiKeyValidationResult result = MerchantApiKeyValidationResult.valid(merchantId);

        when(apiKeyService.validateApiKey("sk_live_valid_token")).thenReturn(result);

        mockMvc.perform(post("/internal/v1/api-keys/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()));
    }
}
