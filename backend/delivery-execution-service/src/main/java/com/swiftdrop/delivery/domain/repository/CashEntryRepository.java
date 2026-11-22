package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.CashEntry;
import com.swiftdrop.delivery.domain.enums.CashEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashEntryRepository extends JpaRepository<CashEntry, Long> {

    String UNSUBMITTED = "e.entryType = com.swiftdrop.delivery.domain.enums.CashEntryType.COLLECTION "
            + "AND e.handoverId IS NULL";
    String OPEN_REFUND = "e.entryType = com.swiftdrop.delivery.domain.enums.CashEntryType.REFUND "
            + "AND e.depositId IS NULL AND (:district IS NULL OR e.district = :district)";

    /** Cash the courier still holds, newest first. */
    @Query("SELECT e FROM CashEntry e WHERE e.courierId = :courierId AND " + UNSUBMITTED + " ORDER BY e.recordedAt DESC")
    List<CashEntry> findInHand(@Param("courierId") UUID courierId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM CashEntry e WHERE e.courierId = :courierId AND " + UNSUBMITTED)
    BigDecimal sumInHand(@Param("courierId") UUID courierId);

    List<CashEntry> findByHandoverIdOrderByRecordedAtDesc(UUID handoverId);

    Optional<CashEntry> findByDeliveryIdAndEntryType(UUID deliveryId, CashEntryType entryType);

    /**
     * Puts exactly the entries listed into the handover. Fewer rows than listed means another request took
     * some of them first.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE CashEntry e SET e.handoverId = :handoverId WHERE e.entryId IN :entryIds AND e.handoverId IS NULL")
    int attachToHandover(@Param("handoverId") UUID handoverId, @Param("entryIds") List<UUID> entryIds);

    /** Couriers holding cash, most first; every district when {@code district} is null. */
    @Query("SELECT e.courierId AS courierId, MAX(e.courierName) AS courierName, SUM(e.amount) AS amount, "
            + "COUNT(e) AS parcels, MIN(e.recordedAt) AS since FROM CashEntry e WHERE " + UNSUBMITTED
            + " AND (:district IS NULL OR e.district = :district) GROUP BY e.courierId ORDER BY SUM(e.amount) DESC")
    List<CourierHolding> findHoldings(@Param("district") SriLankanDistrict district);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM CashEntry e WHERE " + OPEN_REFUND)
    BigDecimal sumOpenRefunds(@Param("district") SriLankanDistrict district);

    @Query("SELECT e FROM CashEntry e WHERE " + OPEN_REFUND + " ORDER BY e.recordedAt DESC")
    List<CashEntry> findOpenRefunds(@Param("district") SriLankanDistrict district);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE CashEntry e SET e.depositId = :depositId WHERE e.entryId IN :entryIds AND e.depositId IS NULL")
    int attachRefundsToDeposit(@Param("depositId") UUID depositId, @Param("entryIds") List<UUID> entryIds);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE CashEntry e SET e.depositId = NULL WHERE e.depositId = :depositId")
    int releaseRefunds(@Param("depositId") UUID depositId);

    /** A courier's cash still in their hands (not yet submitted). */
    interface CourierHolding {
        UUID getCourierId();
        String getCourierName();
        BigDecimal getAmount();
        long getParcels();
        Instant getSince();
    }
}
