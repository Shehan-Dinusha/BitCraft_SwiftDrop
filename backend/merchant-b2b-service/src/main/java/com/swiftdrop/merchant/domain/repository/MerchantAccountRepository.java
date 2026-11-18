package com.swiftdrop.merchant.domain.repository;

import com.swiftdrop.merchant.domain.entity.MerchantAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import com.swiftdrop.merchant.domain.entity.MerchantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, Long> {
    Optional<MerchantAccount> findByMerchantId(UUID merchantId);
    Optional<MerchantAccount> findByContactEmail(String contactEmail);
    List<MerchantAccount> findByStatus(MerchantStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MerchantAccount m where m.merchantId = :merchantId")
    Optional<MerchantAccount> findForUpdate(@Param("merchantId") UUID merchantId);
}
