package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.entity.AiToolInvocationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiToolInvocationAuditRepository extends JpaRepository<AiToolInvocationAudit, Long> {

    List<AiToolInvocationAudit> findTop20ByOrderByCreatedAtDesc();

    List<AiToolInvocationAudit> findAllByToolName(String toolName);
}
