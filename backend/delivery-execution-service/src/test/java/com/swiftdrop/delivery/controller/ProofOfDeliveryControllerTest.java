package com.swiftdrop.delivery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProofOfDeliveryController.class)
class ProofOfDeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProofOfDeliveryStorageService storageService;

    @Test
    @DisplayName("POST /api/v1/deliveries/{deliveryId}/proof-of-delivery uploads evidence")
    void testUploadPod() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        ProofOfDeliveryUploadRequest request = new ProofOfDeliveryUploadRequest(
                "https://storage.swiftdrop.lk/pod/photo.jpg",
                "signature-base64"
        );

        ProofOfDeliveryResponse response = new ProofOfDeliveryResponse(
                1L,
                deliveryId,
                true,
                Instant.now(),
                Instant.now()
        );

        when(storageService.store(eq(deliveryId), any(ProofOfDeliveryUploadRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryId").value(deliveryId.toString()))
                .andExpect(jsonPath("$.data.verified").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/deliveries/{deliveryId}/proof-of-delivery with multipart/form-data uploads evidence")
    void testUploadPodMultipart() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "evidence.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );

        ProofOfDeliveryResponse response = new ProofOfDeliveryResponse(
                2L,
                deliveryId,
                true,
                Instant.now(),
                Instant.now()
        );

        when(storageService.storeMultipart(eq(deliveryId), any(), eq("Kamal Silva"), eq("sig-data")))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .file(photo)
                        .param("recipientName", "Kamal Silva")
                        .param("signature", "sig-data"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryId").value(deliveryId.toString()))
                .andExpect(jsonPath("$.data.verified").value(true));
    }
}
