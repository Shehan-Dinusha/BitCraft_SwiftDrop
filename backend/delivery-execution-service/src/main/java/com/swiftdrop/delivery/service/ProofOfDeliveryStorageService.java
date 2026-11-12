package com.swiftdrop.delivery.service;

import com.swiftdrop.delivery.domain.entity.ProofOfDelivery;
import com.swiftdrop.delivery.domain.repository.ProofOfDeliveryRepository;
import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

@Service
public class ProofOfDeliveryStorageService {
    private final ProofOfDeliveryRepository repository;
    private final String storageDir;

    public ProofOfDeliveryStorageService(ProofOfDeliveryRepository repository) {
        this(repository, System.getenv("POD_STORAGE_DIR") != null ? System.getenv("POD_STORAGE_DIR") : "/tmp/swiftdrop/pod");
    }

    public ProofOfDeliveryStorageService(
            ProofOfDeliveryRepository repository,
            @Value("${swiftdrop.delivery.pod-storage-dir:${POD_STORAGE_DIR:/tmp/swiftdrop/pod}}") String storageDir) {
        this.repository = repository;
        this.storageDir = storageDir != null && !storageDir.isBlank() ? storageDir : "/tmp/swiftdrop/pod";
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
    public ProofOfDeliveryResponse storeMultipart(UUID deliveryId, MultipartFile photo, String recipientName, String signature, String otp) {
        validateMultipart(photo, recipientName);
        ProofOfDelivery pod = new ProofOfDelivery();
        pod.setDeliveryId(deliveryId);
        pod.setRecipientName(recipientName != null ? recipientName.trim() : null);
        pod.setRecipientSignature(signature);
        pod.setConfirmationOtp(otp);
        pod.setCreatedAt(Instant.now());
        pod.setVerified(false);

        String contentType = photo.getContentType();
        String extension = ".jpg";
        if ("image/png".equalsIgnoreCase(contentType)) {
            extension = ".png";
        }

        String fileName = UUID.randomUUID() + extension;
        Path directoryPath = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directoryPath);
            Path targetPath = directoryPath.resolve(fileName).normalize();

            if (!targetPath.startsWith(directoryPath)) {
                throw new SecurityException("Invalid target file path");
            }

            Files.copy(photo.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            pod.setPhotoUrl(targetPath.toString());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store proof-of-delivery photo", ex);
        }

        return toResponse(repository.save(pod));
    }

    private void validateMultipart(MultipartFile photo, String recipientName) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Proof photo file is required");
        }
        if (photo.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Photo exceeds maximum allowed size of 5 MB");
        }
        String contentType = photo.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg") && !contentType.equalsIgnoreCase("image/png") && !contentType.equalsIgnoreCase("image/jpg"))) {
            throw new IllegalArgumentException("Only JPEG and PNG images are allowed");
        }
        if (recipientName == null || recipientName.isBlank()) {
            throw new IllegalArgumentException("Recipient name is required");
        }
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
