package com.swiftdrop.delivery.service;

import com.swiftdrop.delivery.domain.entity.ProofOfDelivery;
import com.swiftdrop.delivery.domain.repository.ProofOfDeliveryRepository;
import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProofOfDeliveryStorageServiceTest {

    @Mock
    private ProofOfDeliveryRepository repository;

    @InjectMocks
    private ProofOfDeliveryStorageService storageService;

    private UUID deliveryId;

    @BeforeEach
    void setUp() {
        deliveryId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Store PoD: persists photo and signature and marks the proof verified")
    void testStorePod_Success() {
        ProofOfDeliveryUploadRequest request = new ProofOfDeliveryUploadRequest(
                "https://storage.swiftdrop.lk/pod/photo1.jpg",
                "base64-encoded-signature"
        );

        when(repository.save(any(ProofOfDelivery.class))).thenAnswer(inv -> {
            ProofOfDelivery p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });

        ProofOfDeliveryResponse response = storageService.store(deliveryId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.deliveryId()).isEqualTo(deliveryId);
        assertThat(response.verified()).isTrue();
        assertThat(response.verifiedAt()).isNotNull();

        verify(repository).save(argThat(p ->
                p.getDeliveryId().equals(deliveryId)
                        && p.getRecipientSignature().equals("base64-encoded-signature")
                        && p.isVerified()
        ));
    }

    @Test
    @DisplayName("Store PoD: rejects a request without a signature")
    void testStorePod_MissingSignature() {
        ProofOfDeliveryUploadRequest request = new ProofOfDeliveryUploadRequest(
                "https://storage.swiftdrop.lk/pod/photo1.jpg", " ");

        assertThatThrownBy(() -> storageService.store(deliveryId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("isVerified: true once a proof has been stored")
    void testIsVerified_AfterStore() {
        ProofOfDelivery pod = new ProofOfDelivery();
        pod.setDeliveryId(deliveryId);
        pod.setVerified(true);
        when(repository.findTopByDeliveryIdOrderByCreatedAtDesc(deliveryId)).thenReturn(Optional.of(pod));

        assertThat(storageService.isVerified(deliveryId)).isTrue();
    }

    @Test
    @DisplayName("Store multipart PoD: writes file to storage directory with UUID filename and stores path in DB")
    void testStoreMultipart_WritesFileSafely(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        ProofOfDeliveryStorageService customStorage = new ProofOfDeliveryStorageService(repository, tempDir.toString());

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "photo",
                "malicious../name.jpg",
                "image/jpeg",
                "test-photo-data".getBytes()
        );

        when(repository.save(any(ProofOfDelivery.class))).thenAnswer(inv -> inv.getArgument(0));

        ProofOfDeliveryResponse response = customStorage.storeMultipart(deliveryId, file, "Sunil Perera", "sig-123");

        assertThat(response).isNotNull();
        verify(repository).save(argThat(pod -> {
            assertThat(pod.getRecipientName()).isEqualTo("Sunil Perera");
            assertThat(pod.isVerified()).isTrue();
            assertThat(pod.getVerifiedAt()).isNotNull();
            assertThat(pod.getPhotoUrl()).startsWith(tempDir.toString());
            assertThat(pod.getPhotoUrl()).doesNotContain("malicious");
            assertThat(pod.getPhotoUrl()).endsWith(".jpg");
            assertThat(java.nio.file.Files.exists(java.nio.file.Paths.get(pod.getPhotoUrl()))).isTrue();
            return true;
        }));
    }

    @Test
    @DisplayName("Store multipart PoD: falls back to local storage when Azure connection fails")
    void testStoreMultipart_AzureFallback(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        ProofOfDeliveryStorageService azureStorage = new ProofOfDeliveryStorageService(
                repository,
                tempDir.toString(),
                "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=dummykey==;EndpointSuffix=core.windows.net",
                "pod-images"
        );

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "photo",
                "package.png",
                "image/png",
                "sample-png-bytes".getBytes()
        );

        when(repository.save(any(ProofOfDelivery.class))).thenAnswer(inv -> inv.getArgument(0));

        ProofOfDeliveryResponse response = azureStorage.storeMultipart(deliveryId, file, "Kamal Silva", "sig-456");

        assertThat(response).isNotNull();
        verify(repository).save(argThat(pod -> {
            assertThat(pod.getRecipientName()).isEqualTo("Kamal Silva");
            assertThat(pod.getPhotoUrl()).startsWith(tempDir.toString());
            assertThat(pod.getPhotoUrl()).endsWith(".png");
            assertThat(java.nio.file.Files.exists(java.nio.file.Paths.get(pod.getPhotoUrl()))).isTrue();
            return true;
        }));
    }
}
