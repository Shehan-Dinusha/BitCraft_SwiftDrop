package com.swiftdrop.iam.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A user's profile picture (see V6). Keyed by the internal user_accounts.id. */
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "image", nullable = false)
    private byte[] image;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAvatar() {
    }

    public UserAvatar(Long userId, String contentType, byte[] image, Instant updatedAt) {
        this.userId = userId;
        this.contentType = contentType;
        this.image = image;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getImage() {
        return image;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void replace(String contentType, byte[] image, Instant updatedAt) {
        this.contentType = contentType;
        this.image = image;
        this.updatedAt = updatedAt;
    }
}
