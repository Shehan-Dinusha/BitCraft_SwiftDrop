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
    @DisplayName("Store PoD: successfully persists photo, signature, and confirmation OTP")
    void testStorePod_Success() {
        ProofOfDeliveryUploadRequest request = new ProofOfDeliveryUploadRequest(
                "https://storage.swiftdrop.lk/pod/photo1.jpg",
                "base64-encoded-signature",
                "984215"
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
        assertThat(response.verified()).isFalse();

        verify(repository).save(argThat(p ->
                p.getDeliveryId().equals(deliveryId)
                        && p.getConfirmationOtp().equals("984215")
                        && !p.isVerified()
        ));
    }

    @Test
    @DisplayName("Verify PoD: successfully verifies with valid recipient OTP")
    void testVerifyPod_Success() {
        ProofOfDelivery pod = new ProofOfDelivery();
        pod.setId(101L);
        pod.setDeliveryId(deliveryId);
        pod.setConfirmationOtp("984215");
        pod.setVerified(false);

        when(repository.findTopByDeliveryIdOrderByCreatedAtDesc(deliveryId)).thenReturn(Optional.of(pod));
        when(repository.save(any(ProofOfDelivery.class))).thenAnswer(inv -> inv.getArgument(0));

        ProofOfDeliveryResponse response = storageService.verify(deliveryId, "984215");

        assertThat(response.verified()).isTrue();
        assertThat(response.verifiedAt()).isNotNull();
        verify(repository).save(argThat(ProofOfDelivery::isVerified));
    }

    @Test
    @DisplayName("Verify PoD: throws IllegalArgumentException with wrong OTP")
    void testVerifyPod_InvalidOtp() {
        ProofOfDelivery pod = new ProofOfDelivery();
        pod.setDeliveryId(deliveryId);
        pod.setConfirmationOtp("984215");
        pod.setVerified(false);

        when(repository.findTopByDeliveryIdOrderByCreatedAtDesc(deliveryId)).thenReturn(Optional.of(pod));

        assertThatThrownBy(() -> storageService.verify(deliveryId, "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTP is invalid");
    }
}
