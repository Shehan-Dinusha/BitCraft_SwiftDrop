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

    /** The district's own orders: picked up there (first leg) or taken over at its hub (last leg). */
    String HANDLED_BY = "((a.leg = com.swiftdrop.dispatch.domain.enums.AssignmentLeg.FIRST_MILE AND a.pickupDistrict = :district) "
            + "OR (a.leg = com.swiftdrop.dispatch.domain.enums.AssignmentLeg.LAST_MILE AND a.deliveryDistrict = :district))";

    @Query("SELECT a FROM DispatchAssignment a WHERE " + HANDLED_BY)
    Page<DispatchAssignment> findHandledBy(@Param("district") SriLankanDistrict district, Pageable pageable);

    @Query("SELECT a FROM DispatchAssignment a WHERE a.status = :status AND " + HANDLED_BY)
    Page<DispatchAssignment> findHandledByAndStatus(@Param("district") SriLankanDistrict district,
                                                    @Param("status") AssignmentStatus status, Pageable pageable);

    @Query("SELECT a FROM DispatchAssignment a WHERE a.status = :status AND " + HANDLED_BY + " ORDER BY a.createdAt")
    List<DispatchAssignment> findHandledByWithStatus(@Param("district") SriLankanDistrict district,
                                                     @Param("status") AssignmentStatus status);

    /** Handed in at the pickup district's hub and not yet marked Outbound: ready to send. */
    @Query("SELECT a FROM DispatchAssignment a WHERE a.pickupDistrict = :district "
            + "AND a.status = com.swiftdrop.dispatch.domain.enums.AssignmentStatus.IN_TRANSFER "
            + "AND a.hubStage = com.swiftdrop.contracts.enums.InternalQueueStatus.AT_HUB ORDER BY a.updatedAt")
    List<DispatchAssignment> findReadyToSend(@Param("district") SriLankanDistrict district);

    /**
     * Orders coming into a district from another one, until its hub accepts them (they then become the
     * district's own last-mile orders). Those marked Outbound (hubStage OUTBOUND) can be accepted.
     */
    @Query("SELECT a FROM DispatchAssignment a WHERE a.deliveryDistrict = :district AND a.pickupDistrict <> :district "
            + "AND a.leg = com.swiftdrop.dispatch.domain.enums.AssignmentLeg.FIRST_MILE "
            + "AND a.status NOT IN (com.swiftdrop.dispatch.domain.enums.AssignmentStatus.COMPLETED, "
            + "com.swiftdrop.dispatch.domain.enums.AssignmentStatus.CANCELLED) ORDER BY a.createdAt DESC")
    List<DispatchAssignment> findIncoming(@Param("district") SriLankanDistrict district);
}
