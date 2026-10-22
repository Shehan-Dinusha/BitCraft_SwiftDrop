package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.entity.AiToolInvocationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiToolInvocationAuditRepository extends JpaRepository<AiToolInvocationAudit, Long> {
}
