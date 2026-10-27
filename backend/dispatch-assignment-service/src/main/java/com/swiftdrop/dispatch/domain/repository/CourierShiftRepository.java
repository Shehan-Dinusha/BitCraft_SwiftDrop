package com.swiftdrop.dispatch.domain.repository;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourierShiftRepository extends JpaRepository<CourierShift, Long> {

    Optional<CourierShift> findByCourierId(UUID courierId);

    List<CourierShift> findByCurrentDistrictAndShiftStatus(SriLankanDistrict district, ShiftStatus status);

    List<CourierShift> findByShiftStatus(ShiftStatus status);

    List<CourierShift> findByCurrentDistrict(SriLankanDistrict district);

    @Query("SELECT c FROM CourierShift c WHERE c.currentDistrict = :district AND c.shiftStatus = :status AND c.activeParcelsCount < c.maxCapacity ORDER BY c.activeParcelsCount ASC, c.lastHeartbeatAt DESC")
    List<CourierShift> findAvailableCandidates(@Param("district") SriLankanDistrict district, @Param("status") ShiftStatus status);
}
