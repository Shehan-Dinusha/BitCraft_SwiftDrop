package com.swiftdrop.delivery.controller;

import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.exception.GlobalExceptionHandler;
import com.swiftdrop.delivery.service.ProofOfDeliveryStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProofOfDeliveryController.class)
@Import(GlobalExceptionHandler.class)
class PodUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProofOfDeliveryStorageService storageService;

    @Test
    @DisplayName("Upload photo > 5 MB returns 400 Bad Request")
    void testUploadPhotoExceeds5MB_Returns400() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6 MB

        MockMultipartFile largePhoto = new MockMultipartFile(
                "photo",
                "large_photo.jpg",
                "image/jpeg",
                largeContent
        );

        mockMvc.perform(multipart("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .file(largePhoto)
                        .param("recipientName", "Kamal Perera"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Photo exceeds maximum allowed size of 5 MB"));
    }

    @Test
    @DisplayName("Upload non-image file returns 400 Bad Request")
    void testUploadNonImage_Returns400() throws Exception {
        UUID deliveryId = UUID.randomUUID();

        MockMultipartFile pdfFile = new MockMultipartFile(
                "photo",
                "document.pdf",
                "application/pdf",
                "dummy pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .file(pdfFile)
                        .param("recipientName", "Kamal Perera"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Only JPEG and PNG images are allowed"));
    }

    @Test
    @DisplayName("Upload text file masquerading as photo returns 400 Bad Request")
    void testUploadTextFile_Returns400() throws Exception {
        UUID deliveryId = UUID.randomUUID();

        MockMultipartFile textFile = new MockMultipartFile(
                "photo",
                "script.sh",
                "text/plain",
                "echo hello".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .file(textFile)
                        .param("recipientName", "Kamal Perera"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Only JPEG and PNG images are allowed"));
    }

    @Test
    @DisplayName("Upload empty photo returns 400 Bad Request")
    void testUploadEmptyPhoto_Returns400() throws Exception {
        UUID deliveryId = UUID.randomUUID();

        MockMultipartFile emptyFile = new MockMultipartFile(
                "photo",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .file(emptyFile)
                        .param("recipientName", "Kamal Perera"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Proof photo file is required"));
    }

    @Test
    @DisplayName("Upload missing recipientName returns 400 Bad Request")
    void testUploadMissingRecipientName_Returns400() throws Exception {
        UUID deliveryId = UUID.randomUUID();

        MockMultipartFile validPhoto = new MockMultipartFile(
                "photo",
                "delivery.jpg",
                "image/jpeg",
                "valid photo content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .file(validPhoto)
                        .param("recipientName", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Recipient name is required"));
    }

    @Test
    @DisplayName("Upload valid JPEG photo <= 5 MB returns 201 Created")
    void testUploadValidJpegPhoto_Returns201() throws Exception {
        UUID deliveryId = UUID.randomUUID();

        MockMultipartFile validPhoto = new MockMultipartFile(
                "photo",
                "delivered.jpg",
                "image/jpeg",
                "valid photo content".getBytes()
        );

        ProofOfDeliveryResponse response = new ProofOfDeliveryResponse(
                55L,
                deliveryId,
                false,
                Instant.now(),
                null
        );

        when(storageService.storeMultipart(eq(deliveryId), any(), eq("Kamal Perera"), any(), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .file(validPhoto)
                        .param("recipientName", "Kamal Perera"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryId").value(deliveryId.toString()))
                .andExpect(jsonPath("$.data.verified").value(false));
    }

    @Test
    @DisplayName("Upload valid PNG photo returns 201 Created")
    void testUploadValidPngPhoto_Returns201() throws Exception {
        UUID deliveryId = UUID.randomUUID();

        MockMultipartFile validPhoto = new MockMultipartFile(
                "photo",
                "delivered.png",
                "image/png",
                "png bytes".getBytes()
        );

        ProofOfDeliveryResponse response = new ProofOfDeliveryResponse(
                56L,
                deliveryId,
                false,
                Instant.now(),
                null
        );

        when(storageService.storeMultipart(eq(deliveryId), any(), eq("Nimal Fernando"), any(), any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/deliveries/" + deliveryId + "/proof-of-delivery")
                        .file(validPhoto)
                        .param("recipientName", "Nimal Fernando"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deliveryId").value(deliveryId.toString()));
    }
}

