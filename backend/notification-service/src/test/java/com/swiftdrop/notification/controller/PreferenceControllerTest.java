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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    /** Simulates the verified identity headers the gateway adds after JWT validation. */
    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, UUID userId, String role) {
        return builder.header("X-User-Id", userId.toString()).header("X-User-Role", role);
    }

    private RecipientPreferenceResponse prefs(UUID owner, String email) {
        return new RecipientPreferenceResponse(owner, "CUSTOMER", true, false, email, "en", Instant.now());
    }

    private String update(String email) throws Exception {
        return objectMapper.writeValueAsString(new UpdatePreferenceRequest(true, true, email, "si"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/preferences/health should return 200 OK")
    void testHealth() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("A user can read their own preferences")
    void testGetPreferences() throws Exception {
        when(preferenceService.getOrCreatePreferences(recipientId)).thenReturn(prefs(recipientId, "user@example.com"));

        mockMvc.perform(as(get("/api/v1/notifications/preferences/{recipientId}", recipientId), recipientId, "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipientId").value(recipientId.toString()))
                .andExpect(jsonPath("$.data.emailEnabled").value(true))
                .andExpect(jsonPath("$.data.pushEnabled").value(false))
                .andExpect(jsonPath("$.data.emailAddress").value("user@example.com"))
                .andExpect(jsonPath("$.data.smsEnabled").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist());
    }

    @Test
    @DisplayName("Reading another user's preferences returns 403; a dispatcher may read them")
    void testReadOtherUsersPreferences() throws Exception {
        when(preferenceService.getOrCreatePreferences(otherUserId)).thenReturn(prefs(otherUserId, "other@example.com"));

        mockMvc.perform(as(get("/api/v1/notifications/preferences/{recipientId}", otherUserId), recipientId, "CUSTOMER"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/v1/notifications/preferences/{recipientId}", otherUserId), staffId, "DISPATCHER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /preferences returns the caller's own preferences")
    void testGetCurrentUserPreferences() throws Exception {
        when(preferenceService.getOrCreatePreferences(recipientId)).thenReturn(prefs(recipientId, "user@example.com"));

        mockMvc.perform(as(get("/api/v1/notifications/preferences"), recipientId, "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientId").value(recipientId.toString()));
        mockMvc.perform(as(get("/api/v1/notifications/preferences"), recipientId, "CUSTOMER")
                        .param("recipientId", otherUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A user can update their own preferences")
    void testUpdatePreferences() throws Exception {
        when(preferenceService.updatePreferences(eq(recipientId), any(UpdatePreferenceRequest.class)))
                .thenReturn(new RecipientPreferenceResponse(recipientId, "CUSTOMER", true, true,
                        "updated@example.com", "si", Instant.now()));

        mockMvc.perform(as(put("/api/v1/notifications/preferences/{recipientId}", recipientId), recipientId, "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("updated@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.emailAddress").value("updated@example.com"))
                .andExpect(jsonPath("$.data.languagePreference").value("si"));
    }

    @Test
    @DisplayName("Nobody but the user or a system admin can change their preferences (redirect protection)")
    void testCannotUpdateOtherUsersPreferences() throws Exception {
        mockMvc.perform(as(put("/api/v1/notifications/preferences/{recipientId}", otherUserId), recipientId, "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("attacker@example.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put("/api/v1/notifications/preferences/{recipientId}", otherUserId), staffId, "DISPATCHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update("attacker@example.com")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(preferenceService);
    }

    @Test
    @DisplayName("Requests without the gateway identity headers are rejected with 401")
    void testMissingIdentityIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences/{recipientId}", recipientId))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(preferenceService);
    }
}
