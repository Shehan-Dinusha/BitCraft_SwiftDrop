package com.swiftdrop.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.dto.RecipientPreferenceResponse;
import com.swiftdrop.notification.dto.UpdatePreferenceRequest;
import com.swiftdrop.notification.service.RecipientPreferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferenceController.class)
class PreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecipientPreferenceService preferenceService;

    private final UUID recipientId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/notifications/preferences/health should return 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/preferences/{recipientId} should return preferences")
    void testGetPreferences() throws Exception {
        RecipientPreferenceResponse response = new RecipientPreferenceResponse(
                recipientId,
                "CUSTOMER",
                true,
                true,
                false,
                "user@example.com",
                "+94771234567",
                "en",
                Instant.now()
        );

        when(preferenceService.getOrCreatePreferences(recipientId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/notifications/preferences/{recipientId}", recipientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipientId").value(recipientId.toString()))
                .andExpect(jsonPath("$.data.emailEnabled").value(true))
                .andExpect(jsonPath("$.data.smsEnabled").value(true))
                .andExpect(jsonPath("$.data.pushEnabled").value(false))
                .andExpect(jsonPath("$.data.emailAddress").value("user@example.com"))
                .andExpect(jsonPath("$.data.phoneNumber").value("+94771234567"));
    }

    @Test
    @DisplayName("PUT /api/v1/notifications/preferences/{recipientId} should update preferences")
    void testUpdatePreferences() throws Exception {
        UpdatePreferenceRequest request = new UpdatePreferenceRequest(
                true,
                false,
                true,
                "updated@example.com",
                "+94779876543",
                "si"
        );

        RecipientPreferenceResponse response = new RecipientPreferenceResponse(
                recipientId,
                "CUSTOMER",
                true,
                false,
                true,
                "updated@example.com",
                "+94779876543",
                "si",
                Instant.now()
        );

        when(preferenceService.updatePreferences(eq(recipientId), any(UpdatePreferenceRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/notifications/preferences/{recipientId}", recipientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.emailAddress").value("updated@example.com"))
                .andExpect(jsonPath("$.data.smsEnabled").value(false))
                .andExpect(jsonPath("$.data.languagePreference").value("si"));
    }
}
