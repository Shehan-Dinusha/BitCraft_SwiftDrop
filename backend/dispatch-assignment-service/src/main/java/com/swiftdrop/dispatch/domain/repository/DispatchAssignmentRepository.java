package com.swiftdrop.dispatch.domain.repository;

import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispatchAssignmentRepository extends JpaRepository<DispatchAssignment, Long> {

    Optional<DispatchAssignment> findByAssignmentId(UUID assignmentId);

    Optional<DispatchAssignment> findByOrderId(UUID orderId);

    Optional<DispatchAssignment> findByTrackingNumber(String trackingNumber);

    List<DispatchAssignment> findByCourierId(UUID courierId);

    List<DispatchAssignment> findByCourierIdAndStatus(UUID courierId, AssignmentStatus status);

    List<DispatchAssignment> findByStatus(AssignmentStatus status);

    Page<DispatchAssignment> findByStatus(AssignmentStatus status, Pageable pageable);

    Page<DispatchAssignment> findByCourierId(UUID courierId, Pageable pageable);
}
