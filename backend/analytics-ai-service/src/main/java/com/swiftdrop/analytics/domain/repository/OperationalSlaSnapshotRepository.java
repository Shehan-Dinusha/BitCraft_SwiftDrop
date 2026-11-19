package com.swiftdrop.analytics.domain.repository;

import com.swiftdrop.analytics.domain.entity.OperationalSlaSnapshot;
import com.swiftdrop.contracts.enums.SriLankanDistrict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OperationalSlaSnapshotRepository extends JpaRepository<OperationalSlaSnapshot, Long> {

    Optional<OperationalSlaSnapshot> findByDistrictAndSnapshotDate(SriLankanDistrict district, LocalDate snapshotDate);

    List<OperationalSlaSnapshot> findAllBySnapshotDate(LocalDate snapshotDate);

    List<OperationalSlaSnapshot> findAllByDistrict(SriLankanDistrict district);
}
