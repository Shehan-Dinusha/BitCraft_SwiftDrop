package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.entity.AiToolInvocationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiToolInvocationAuditRepository extends JpaRepository<AiToolInvocationAudit, Long> {

    List<AiToolInvocationAudit> findTop20ByOrderByCreatedAtDesc();

    List<AiToolInvocationAudit> findAllByToolName(String toolName);
}
