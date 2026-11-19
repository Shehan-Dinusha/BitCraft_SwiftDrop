package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    Optional<NotificationTemplate> findByTemplateCode(String templateCode);
    Optional<NotificationTemplate> findByTemplateCodeAndActiveTrue(String templateCode);
}
