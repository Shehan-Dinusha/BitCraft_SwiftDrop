package com.swiftdrop.delivery.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.swiftdrop.delivery.domain.entity.ProofOfDelivery;
import com.swiftdrop.delivery.domain.repository.ProofOfDeliveryRepository;
import com.swiftdrop.delivery.dto.ProofOfDeliveryResponse;
import com.swiftdrop.delivery.dto.ProofOfDeliveryUploadRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

@Service
public class ProofOfDeliveryStorageService {
    private static final Logger log = LoggerFactory.getLogger(ProofOfDeliveryStorageService.class);

    private final ProofOfDeliveryRepository repository;
    private final String storageDir;
    private final String azureConnectionString;
    private final String azureContainerName;

    public ProofOfDeliveryStorageService() {
        this(null, "/tmp/swiftdrop/pod", null, "pod-images");
    }

    public ProofOfDeliveryStorageService(ProofOfDeliveryRepository repository) {
        this(repository,
                System.getenv("POD_STORAGE_DIR") != null ? System.getenv("POD_STORAGE_DIR") : "/tmp/swiftdrop/pod",
                System.getenv("AZURE_STORAGE_CONNECTION_STRING"),
                System.getenv("AZURE_STORAGE_CONTAINER_NAME") != null ? System.getenv("AZURE_STORAGE_CONTAINER_NAME") : "pod-images");
    }

    public ProofOfDeliveryStorageService(ProofOfDeliveryRepository repository, String storageDir) {
        this(repository, storageDir, null, "pod-images");
    }

    @Autowired
    public ProofOfDeliveryStorageService(
            ProofOfDeliveryRepository repository,
            @Value("${swiftdrop.delivery.pod-storage-dir:${POD_STORAGE_DIR:/tmp/swiftdrop/pod}}") String storageDir,
            @Value("${azure.storage.connection-string:${AZURE_STORAGE_CONNECTION_STRING:}}") String azureConnectionString,
            @Value("${azure.storage.container-name:${AZURE_STORAGE_CONTAINER_NAME:pod-images}}") String azureContainerName) {
        this.repository = repository;
        this.storageDir = storageDir != null && !storageDir.isBlank() ? storageDir : "/tmp/swiftdrop/pod";
        this.azureConnectionString = azureConnectionString != null ? azureConnectionString.trim() : "";
        this.azureContainerName = azureContainerName != null && !azureContainerName.isBlank() ? azureContainerName.trim() : "pod-images";
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

        // 1. Upload to Azure Blob Storage if connection string is configured
        if (azureConnectionString != null && !azureConnectionString.isBlank()) {
            try {
                BlobContainerClient containerClient = new BlobContainerClientBuilder()
                        .connectionString(azureConnectionString)
                        .containerName(azureContainerName)
                        .buildClient();

                containerClient.createIfNotExists();

                BlobClient blobClient = containerClient.getBlobClient(fileName);
                BlobHttpHeaders headers = new BlobHttpHeaders()
                        .setContentType(contentType != null ? contentType : "image/jpeg");

                try (InputStream inputStream = photo.getInputStream()) {
                    blobClient.upload(inputStream, photo.getSize(), true);
                    blobClient.setHttpHeaders(headers);
                }

                String blobUrl = blobClient.getBlobUrl();
                log.info("Proof-of-delivery photo successfully uploaded to Azure Blob: {}", blobUrl);
                pod.setPhotoUrl(blobUrl);
                return toResponse(repository.save(pod));
            } catch (Exception ex) {
                log.warn("Azure Blob upload failed, falling back to local filesystem storage: {}", ex.getMessage());
            }
        }

        // 2. Fallback to local filesystem storage
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
