package com.swiftdrop.delivery.service;

import com.swiftdrop.delivery.domain.entity.ProofOfDelivery;
import com.swiftdrop.delivery.domain.repository.ProofOfDeliveryRepository;
import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProofOfDeliveryStorageService {
    private final ProofOfDeliveryRepository repository;

    public ProofOfDeliveryStorageService(ProofOfDeliveryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProofOfDeliveryResponse store(UUID deliveryId, ProofOfDeliveryUploadRequest request) {
        validate(request);
        ProofOfDelivery pod = new ProofOfDelivery();
        pod.setDeliveryId(deliveryId);
        pod.setPhotoUrl(request.photoUrl());
        pod.setRecipientSignature(request.recipientSignature());
        pod.setConfirmationOtp(request.confirmationOtp());
        pod.setCreatedAt(Instant.now());
        pod.setVerified(false);
        return toResponse(repository.save(pod));
    }

    @Transactional
    public ProofOfDeliveryResponse verify(UUID deliveryId, String otp) {
        ProofOfDelivery pod = repository.findTopByDeliveryIdOrderByCreatedAtDesc(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("No proof of delivery exists for " + deliveryId));
        if (otp == null || !otp.equals(pod.getConfirmationOtp())) {
            throw new IllegalArgumentException("Proof-of-delivery OTP is invalid");
        }
        pod.setVerified(true);
        pod.setVerifiedAt(Instant.now());
        return toResponse(repository.save(pod));
    }

    public boolean isVerified(UUID deliveryId) {
        return repository.findTopByDeliveryIdOrderByCreatedAtDesc(deliveryId)
                .map(pod -> pod.isVerified())
                .orElse(false);
    }

    public ProofOfDeliveryResponse getByDeliveryId(UUID deliveryId) {
        return repository.findTopByDeliveryIdOrderByCreatedAtDesc(deliveryId)
                .map(this::toResponse)
                .orElse(null);
    }

    private void validate(ProofOfDeliveryUploadRequest request) {
        if (request == null || isBlank(request.photoUrl()) || isBlank(request.recipientSignature())
                || isBlank(request.confirmationOtp())) {
            throw new IllegalArgumentException("Photo, recipient signature, and OTP are required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ProofOfDeliveryResponse toResponse(ProofOfDelivery pod) {
        return new ProofOfDeliveryResponse(pod.getId(), pod.getDeliveryId(), pod.isVerified(),
                pod.getCreatedAt(), pod.getVerifiedAt());
    }
}
