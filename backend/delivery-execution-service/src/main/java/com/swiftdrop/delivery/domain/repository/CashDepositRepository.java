package com.swiftdrop.delivery.domain.repository;

import com.swiftdrop.contracts.enums.SriLankanDistrict;
import com.swiftdrop.delivery.domain.entity.CashDeposit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashDepositRepository extends JpaRepository<CashDeposit, Long> {

    Optional<CashDeposit> findByDepositId(UUID depositId);

    @Query("SELECT d FROM CashDeposit d WHERE :district IS NULL OR d.district = :district ORDER BY d.depositedAt DESC")
    List<CashDeposit> findRecent(@Param("district") SriLankanDistrict district, Pageable page);
}
