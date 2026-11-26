package com.swiftdrop.dispatch.domain.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.DispatchAssignment;
import com.swiftdrop.dispatch.domain.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchAssignmentRepository extends JpaRepository<DispatchAssignment, Long> {

    Optional<DispatchAssignment> findByAssignmentId(UUID assignmentId);

    Optional<DispatchAssignment> findByOrderId(UUID orderId);

    Optional<DispatchAssignment> findByTrackingNumber(String trackingNumber);

    List<DispatchAssignment> findByCourierId(UUID courierId);

    List<DispatchAssignment> findByCourierIdAndStatus(UUID courierId, AssignmentStatus status);

    /** The shop's parcels that no courier has collected yet. */
    @Query("SELECT a FROM DispatchAssignment a WHERE a.merchantId = :merchantId AND a.customerId = :merchantId "
            + "AND a.leg = com.swiftdrop.dispatch.domain.enums.AssignmentLeg.FIRST_MILE AND a.pickedUpAt IS NULL "
            + "AND a.status IN (com.swiftdrop.dispatch.domain.enums.AssignmentStatus.UNASSIGNED, "
            + "com.swiftdrop.dispatch.domain.enums.AssignmentStatus.ASSIGNED, "
            + "com.swiftdrop.dispatch.domain.enums.AssignmentStatus.REASSIGNED) ORDER BY a.createdAt")
    List<DispatchAssignment> findAtShop(@Param("merchantId") UUID merchantId);

    List<DispatchAssignment> findByStatus(AssignmentStatus status);

    Page<DispatchAssignment> findByStatus(AssignmentStatus status, Pageable pageable);

    Page<DispatchAssignment> findByCourierId(UUID courierId, Pageable pageable);

    /** The district's own orders: picked up there (first leg and returns) or taken over at its hub (last leg). */
    String HANDLED_BY = "((a.leg <> com.swiftdrop.dispatch.domain.enums.AssignmentLeg.LAST_MILE AND a.pickupDistrict = :district) "
            + "OR (a.leg = com.swiftdrop.dispatch.domain.enums.AssignmentLeg.LAST_MILE AND a.deliveryDistrict = :district))";

    @Query("SELECT a FROM DispatchAssignment a WHERE " + HANDLED_BY)
    Page<DispatchAssignment> findHandledBy(@Param("district") SriLankanDistrict district, Pageable pageable);

    @Query("SELECT a FROM DispatchAssignment a WHERE a.status = :status AND " + HANDLED_BY)
    Page<DispatchAssignment> findHandledByAndStatus(@Param("district") SriLankanDistrict district,
                                                    @Param("status") AssignmentStatus status, Pageable pageable);

    @Query("SELECT a FROM DispatchAssignment a WHERE a.status = :status AND " + HANDLED_BY + " ORDER BY a.createdAt")
    List<DispatchAssignment> findHandledByWithStatus(@Param("district") SriLankanDistrict district,
                                                     @Param("status") AssignmentStatus status);

    /** Weight and volume held by each of these couriers; a courier who holds nothing has no row. */
    @Query("SELECT a.courierId AS courierId, SUM(a.weightKg) AS weightKg, SUM(a.volumeLitres) AS volumeLitres "
            + "FROM DispatchAssignment a WHERE a.courierId IN :courierIds "
            + "AND a.status IN (com.swiftdrop.dispatch.domain.enums.AssignmentStatus.ASSIGNED, "
            + "com.swiftdrop.dispatch.domain.enums.AssignmentStatus.REASSIGNED) GROUP BY a.courierId")
    List<CourierLoadRow> sumActiveLoads(@Param("courierIds") Collection<UUID> courierIds);

    /** Handed in at the pickup district's hub and not yet marked Outbound: ready to send. */
    @Query("SELECT a FROM DispatchAssignment a WHERE a.pickupDistrict = :district "
            + "AND a.status = com.swiftdrop.dispatch.domain.enums.AssignmentStatus.IN_TRANSFER "
            + "AND a.hubStage = com.swiftdrop.contracts.enums.InternalQueueStatus.AT_HUB ORDER BY a.updatedAt")
    List<DispatchAssignment> findReadyToSend(@Param("district") SriLankanDistrict district);

    /**
     * Marked Outbound by the pickup district and not yet accepted at the drop-off hub: sent, on the way. Once
     * accepted it is the drop-off district's last-mile order and leaves this list.
     */
    @Query("SELECT a FROM DispatchAssignment a WHERE a.pickupDistrict = :district "
            + "AND a.status = com.swiftdrop.dispatch.domain.enums.AssignmentStatus.IN_TRANSFER "
            + "AND a.hubStage = com.swiftdrop.contracts.enums.InternalQueueStatus.OUTBOUND ORDER BY a.updatedAt")
    List<DispatchAssignment> findSent(@Param("district") SriLankanDistrict district);

    /**
     * Orders coming into a district from another one, until its hub accepts them (they then become the
     * district's own last-mile orders). Those marked Outbound (hubStage OUTBOUND) can be accepted.
     */
    @Query("SELECT a FROM DispatchAssignment a WHERE a.deliveryDistrict = :district AND a.pickupDistrict <> :district "
            + "AND a.leg = com.swiftdrop.dispatch.domain.enums.AssignmentLeg.FIRST_MILE "
            + "AND a.status NOT IN (com.swiftdrop.dispatch.domain.enums.AssignmentStatus.COMPLETED, "
            + "com.swiftdrop.dispatch.domain.enums.AssignmentStatus.CANCELLED) ORDER BY a.createdAt DESC")
    List<DispatchAssignment> findIncoming(@Param("district") SriLankanDistrict district);

    /**
     * Failed deliveries a district deals with: coming back with a courier or held at its hub (the drop-off
     * district), and returns on their way to its hub (the pickup district).
     */
    @Query("SELECT a FROM DispatchAssignment a WHERE a.status NOT IN ("
            + "com.swiftdrop.dispatch.domain.enums.AssignmentStatus.COMPLETED, "
            + "com.swiftdrop.dispatch.domain.enums.AssignmentStatus.CANCELLED) AND ((a.deliveryDistrict = :district "
            + "AND a.hubStage IN (com.swiftdrop.contracts.enums.InternalQueueStatus.RETURN_TO_HUB, "
            + "com.swiftdrop.contracts.enums.InternalQueueStatus.HELD_AT_HUB)) OR (a.pickupDistrict = :district "
            + "AND a.hubStage = com.swiftdrop.contracts.enums.InternalQueueStatus.RETURN_OUTBOUND)) ORDER BY a.updatedAt")
    List<DispatchAssignment> findReturns(@Param("district") SriLankanDistrict district);
}
