package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.RecipientPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecipientPreferenceRepository extends JpaRepository<RecipientPreference, Long> {
    Optional<RecipientPreference> findByRecipientId(UUID recipientId);
}
