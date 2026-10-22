package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.entity.OperationalSlaSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalSlaSnapshotRepository extends JpaRepository<OperationalSlaSnapshot, Long> {
}
