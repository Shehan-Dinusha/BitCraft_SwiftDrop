package com.swiftdrop.dispatch.domain.repository;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.dispatch.domain.entity.CourierProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierProfileRepository extends JpaRepository<CourierProfile, Long> {

    Optional<CourierProfile> findByCourierId(UUID courierId);

    List<CourierProfile> findByDistrict(SriLankanDistrict district);

    Optional<CourierProfile> findByPlateNumber(String plateNumber);
}
