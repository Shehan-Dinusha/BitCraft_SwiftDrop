package com.swiftdrop.notification.domain.repository;

import com.swiftdrop.notification.domain.entity.NotificationDispatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDispatchLogRepository extends JpaRepository<NotificationDispatchLog, Long> {
}
