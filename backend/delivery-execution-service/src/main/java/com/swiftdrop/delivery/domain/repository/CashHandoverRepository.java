package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.CashHandover;
import com.swiftdrop.delivery.domain.enums.CashHandoverStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashHandoverRepository extends JpaRepository<CashHandover, Long> {

    Optional<CashHandover> findByHandoverId(UUID handoverId);

    List<CashHandover> findByCourierIdOrderBySubmittedAtDesc(UUID courierId, Pageable page);

    @Query("SELECT h FROM CashHandover h WHERE (:district IS NULL OR h.district = :district) AND h.status = :status "
            + "ORDER BY h.submittedAt DESC")
    List<CashHandover> findByDistrictAndStatus(@Param("district") SriLankanDistrict district,
                                               @Param("status") CashHandoverStatus status, Pageable page);

    /** Counted and still at the hub. */
    @Query("SELECT h FROM CashHandover h WHERE (:district IS NULL OR h.district = :district) AND h.depositId IS NULL "
            + "AND h.status = com.swiftdrop.delivery.domain.enums.CashHandoverStatus.COUNTED ORDER BY h.countedAt")
    List<CashHandover> findAtHub(@Param("district") SriLankanDistrict district);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE CashHandover h SET h.depositId = :depositId, h.version = h.version + 1 "
            + "WHERE h.handoverId IN :handoverIds AND h.depositId IS NULL")
    int attachToDeposit(@Param("depositId") UUID depositId, @Param("handoverIds") List<UUID> handoverIds);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE CashHandover h SET h.depositId = NULL, h.version = h.version + 1 WHERE h.depositId = :depositId")
    int release(@Param("depositId") UUID depositId);
}
