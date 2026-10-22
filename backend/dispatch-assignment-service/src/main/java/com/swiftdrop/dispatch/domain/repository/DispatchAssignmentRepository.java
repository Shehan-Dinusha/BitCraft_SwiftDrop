package com.swiftdrop.dispatch.domain.repository;

import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchAssignmentRepository extends JpaRepository<DispatchAssignment, Long> {
}
