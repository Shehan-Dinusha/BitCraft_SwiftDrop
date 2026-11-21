package com.swiftdrop.dispatch.domain.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchAssignmentRepository extends JpaRepository<DispatchAssignment, Long> {

    Optional<DispatchAssignment> findByAssignmentId(UUID assignmentId);

    Optional<DispatchAssignment> findByOrderId(UUID orderId);

    Optional<DispatchAssignment> findByTrackingNumber(String trackingNumber);

    List<DispatchAssignment> findByCourierId(UUID courierId);

    List<DispatchAssignment> findByCourierIdAndStatus(UUID courierId, AssignmentStatus status);

    List<DispatchAssignment> findByStatus(AssignmentStatus status);

    Page<DispatchAssignment> findByStatus(AssignmentStatus status, Pageable pageable);

    Page<DispatchAssignment> findByCourierId(UUID courierId, Pageable pageable);

    Page<DispatchAssignment> findByPickupDistrict(SriLankanDistrict pickupDistrict, Pageable pageable);

    Page<DispatchAssignment> findByPickupDistrictAndStatus(SriLankanDistrict pickupDistrict, AssignmentStatus status,
                                                           Pageable pageable);

    List<DispatchAssignment> findByStatusAndPickupDistrict(AssignmentStatus status, SriLankanDistrict pickupDistrict);

    /** Open orders coming into a district from another one: its dispatcher follows them read-only. */
    @Query("SELECT a FROM DispatchAssignment a WHERE a.deliveryDistrict = :district AND a.pickupDistrict <> :district "
            + "AND a.status NOT IN (com.swiftdrop.dispatch.domain.enums.AssignmentStatus.COMPLETED, "
            + "com.swiftdrop.dispatch.domain.enums.AssignmentStatus.CANCELLED) ORDER BY a.createdAt DESC")
    List<DispatchAssignment> findIncoming(@Param("district") SriLankanDistrict district);
}
