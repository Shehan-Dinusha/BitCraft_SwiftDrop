package com.swiftdrop.delivery.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proof_of_delivery")
public class ProofOfDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID deliveryId;
    private String photoUrl;
    private String recipientSignature;
    private String confirmationOtp;
    private boolean verified;
    private Instant createdAt;
    private Instant verifiedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getDeliveryId() { return deliveryId; }
    public void setDeliveryId(UUID deliveryId) { this.deliveryId = deliveryId; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getRecipientSignature() { return recipientSignature; }
    public void setRecipientSignature(String recipientSignature) { this.recipientSignature = recipientSignature; }
    public String getConfirmationOtp() { return confirmationOtp; }
    public void setConfirmationOtp(String confirmationOtp) { this.confirmationOtp = confirmationOtp; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
