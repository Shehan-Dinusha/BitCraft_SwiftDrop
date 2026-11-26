package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.RecipientEmailStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecipientEmailStopRepository extends JpaRepository<RecipientEmailStop, UUID> {

    Optional<RecipientEmailStop> findByToken(String token);
}
