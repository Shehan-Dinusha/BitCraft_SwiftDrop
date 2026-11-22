package com.swiftdrop.dispatch.domain.repository;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierShift;
import com.swiftdrop.dispatch.domain.enums.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierShiftRepository extends JpaRepository<CourierShift, Long> {

    Optional<CourierShift> findByCourierId(UUID courierId);

    List<CourierShift> findByCurrentDistrictAndShiftStatus(SriLankanDistrict district, ShiftStatus status);

    List<CourierShift> findByShiftStatus(ShiftStatus status);

    List<CourierShift> findByCurrentDistrict(SriLankanDistrict district);

    /** Couriers of a district on shift, whatever their load. */
    List<CourierShift> findByCurrentDistrictAndShiftStatusNot(SriLankanDistrict district, ShiftStatus status);

    List<CourierShift> findByShiftStatusNot(ShiftStatus status);
}
