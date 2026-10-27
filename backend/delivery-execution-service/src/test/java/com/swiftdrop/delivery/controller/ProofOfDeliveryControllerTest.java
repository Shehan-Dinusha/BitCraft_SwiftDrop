package com.swiftdrop.delivery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProofOfDeliveryController.class)
class ProofOfDeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProofOfDeliveryStorageService storageService;

    @Test
    @DisplayName("POST /api/v1/deliveries/{deliveryId}/proof-of-delivery uploads evidence")
    void testUploadPod() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        ProofOfDeliveryUploadRequest request = new ProofOfDeliveryUploadRequest(
                "https://storage.swiftdrop.lk/pod/photo.jpg",
                "signature-base64",
                "123456"
        );

        ProofOfDeliveryResponse response = new ProofOfDeliveryResponse(
                1L,
                deliveryId,
                false,
                Instant.now(),
                null
        );

        when(storageService.store(eq(deliveryId), any(ProofOfDeliveryUploadRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryId").value(deliveryId.toString()))
                .andExpect(jsonPath("$.data.verified").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/deliveries/{deliveryId}/proof-of-delivery/verify verifies OTP")
    void testVerifyPod() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        ProofOfDeliveryResponse response = new ProofOfDeliveryResponse(
                1L,
                deliveryId,
                true,
                Instant.now(),
                Instant.now()
        );

        when(storageService.verify(eq(deliveryId), eq("123456"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery/verify")
                        .param("otp", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true));
    }
}
